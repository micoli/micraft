package org.micoli.micraft.rpg

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.rpg.character.CreateCharacterCommand
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class CreateCharacterCommandTest {
    private val cmd = CreateCharacterCommand()

    @Test
    fun tooFewArgs_sendsNotification() =
        runBlocking<Unit> {
            val session = testSession()
            val context = testContext(sessions = listOf(session), savePlayer = {})
            cmd.execute(session, "Hero warrior 8 8 8", context)
            val notifications = session.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(notifications.isNotEmpty())
            assertNull(session.characterData)
        }

    @Test
    fun alreadyHasCharacter_sendsNotification() =
        runBlocking<Unit> {
            val session = testSession()
            session.characterData =
                CharacterData(
                    id = "existing",
                    name = "Old",
                    characterClass = CharacterClass.MAGE,
                    baseStats = BaseStats(),
                    currentHp = 8,
                    currentMana = 10,
                )
            val context = testContext(sessions = listOf(session), savePlayer = {})
            cmd.execute(session, "NewHero warrior 8 8 8 8 8 8", context)
            val notifications = session.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(notifications.isNotEmpty())
            // Character unchanged
            assertTrue(session.characterData?.name == "Old")
        }

    @Test
    fun unknownClass_sendsNotification() =
        runBlocking<Unit> {
            val session = testSession()
            val context = testContext(sessions = listOf(session), savePlayer = {})
            cmd.execute(session, "Hero unknownclass 8 8 8 8 8 8", context)
            val notifications = session.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(notifications.isNotEmpty())
            assertNull(session.characterData)
        }

    @Test
    fun invalidStatValue_sendsNotification() =
        runBlocking<Unit> {
            val session = testSession()
            val context = testContext(sessions = listOf(session), savePlayer = {})
            cmd.execute(session, "Hero warrior 7 8 8 8 8 8", context)
            val notifications = session.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(notifications.isNotEmpty())
            assertNull(session.characterData)
        }

    @Test
    fun validArgs_createsCharacterAndSaves() =
        runBlocking<Unit> {
            val session = testSession()
            var saved = false
            val context = testContext(sessions = listOf(session), savePlayer = { saved = true })
            // All stats at 8 = 0 cost each, well within 27-point budget
            cmd.execute(session, "Hero warrior 8 8 8 8 8 8", context)
            assertNotNull(session.characterData)
            assertTrue(session.characterData?.name == "Hero")
            assertTrue(session.characterData?.characterClass == CharacterClass.WARRIOR)
            assertTrue(saved)
        }

    @Test
    fun validArgs_sendsCharacterSync() =
        runBlocking<Unit> {
            val session = testSession()
            val context = testContext(sessions = listOf(session), savePlayer = {})
            cmd.execute(session, "Mage mage 8 8 10 10 8 8", context)
            val syncs = session.sent.filterIsInstance<ServerMessage.CharacterSync>()
            assertTrue(syncs.isNotEmpty())
        }

    @Test
    fun budgetExceeded_sendsNotification() =
        runBlocking<Unit> {
            val session = testSession()
            val context = testContext(sessions = listOf(session), savePlayer = {})
            // 15+15+15+15+15+15 = huge cost, exceeds 27 budget
            cmd.execute(session, "Hero warrior 15 15 15 15 15 15", context)
            val notifications = session.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(notifications.isNotEmpty())
            assertNull(session.characterData)
        }
}
