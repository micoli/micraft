package org.micoli.micraft.rpg

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.rpg.character.CharacterCommand
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class CharacterCommandTest {
    private fun makeCharacter(): CharacterData =
        CharacterData(
            id = "char-1",
            name = "Hero",
            characterClass = CharacterClass.WARRIOR,
            baseStats = BaseStats(str = 10, dex = 8, intel = 8, wis = 8, con = 10, cha = 8),
            currentHp = 15,
            currentMana = 0,
        )

    @Test
    fun noCharacter_sendsNotification() =
        runBlocking<Unit> {
            val session = testSession()
            val context = testContext(sessions = listOf(session))
            CharacterCommand().execute(session, "", context)
            val notifications = session.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(notifications.isNotEmpty())
        }

    @Test
    fun noCharacter_doesNotSendCharacterSync() =
        runBlocking<Unit> {
            val session = testSession()
            val context = testContext(sessions = listOf(session))
            CharacterCommand().execute(session, "", context)
            val syncs = session.sent.filterIsInstance<ServerMessage.CharacterSync>()
            assertTrue(syncs.isEmpty())
        }

    @Test
    fun withCharacter_sendsCharacterSync() =
        runBlocking<Unit> {
            val session = testSession()
            session.characterData = makeCharacter()
            val context = testContext(sessions = listOf(session))
            CharacterCommand().execute(session, "", context)
            val syncs = session.sent.filterIsInstance<ServerMessage.CharacterSync>()
            assertTrue(syncs.isNotEmpty())
        }

    @Test
    fun withCharacter_doesNotSendNotification() =
        runBlocking<Unit> {
            val session = testSession()
            session.characterData = makeCharacter()
            val context = testContext(sessions = listOf(session))
            CharacterCommand().execute(session, "", context)
            val notifications = session.sent.filterIsInstance<ServerMessage.Notification>()
            assertTrue(notifications.isEmpty())
        }
}
