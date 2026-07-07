package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class SetCommandTest {

    private val cmd = SetCommand()

    private fun sessionWithChar(
        id: String = "b",
        name: String = "Bob",
        hp: Int = 10,
        mana: Int = 10
    ) =
        testSession(id = id, name = name).also { s ->
            s.characterData =
                CharacterData(
                    id = id,
                    name = name,
                    characterClass = CharacterClass.MAGE,
                    baseStats = BaseStats(con = 10, wis = 10),
                    currentHp = hp,
                    currentMana = mana,
                )
        }

    // ── argument validation ───────────────────────────────────────────────────

    @Test
    fun missingArgs_sendsUsage() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        cmd.execute(caller, "", testContext(sessions = listOf(caller)))
        assertTrue(caller.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun negativeValue_sendsInvalidValue() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar()
        cmd.execute(caller, "hp Bob -5", testContext(sessions = listOf(caller, bob)))
        assertTrue(caller.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
        assertEquals(10, bob.characterData!!.currentHp)
    }

    @Test
    fun nonNumericValue_sendsInvalidValue() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar()
        cmd.execute(caller, "hp Bob abc", testContext(sessions = listOf(caller, bob)))
        assertTrue(caller.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
        assertEquals(10, bob.characterData!!.currentHp)
    }

    @Test
    fun unknownPlayer_sendsNotFound() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        cmd.execute(caller, "hp Nobody 50", testContext(sessions = listOf(caller)))
        assertTrue(caller.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun unknownStat_sendsError() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar()
        cmd.execute(caller, "stamina Bob 50", testContext(sessions = listOf(caller, bob)))
        assertTrue(caller.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    // ── set hp ────────────────────────────────────────────────────────────────

    @Test
    fun setHp_updatesCharacterData() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar(hp = 5)
        cmd.execute(caller, "hp Bob 20", testContext(sessions = listOf(caller, bob)))
        assertNotNull(bob.characterData)
        assertTrue(bob.characterData!!.currentHp > 0)
    }

    @Test
    fun setHp_capsAtMaxHp() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar(hp = 5)
        val maxHp =
            org.micoli.micraft.rpg.character.DerivedStatsCalculator.compute(bob.characterData!!)
                .maxHp
        cmd.execute(caller, "hp Bob 99999", testContext(sessions = listOf(caller, bob)))
        assertEquals(maxHp, bob.characterData!!.currentHp)
    }

    @Test
    fun setHp_zeroIsAllowed() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar(hp = 10)
        cmd.execute(caller, "hp Bob 0", testContext(sessions = listOf(caller, bob)))
        assertEquals(0, bob.characterData!!.currentHp)
    }

    @Test
    fun setHp_broadcastsHealthUpdate() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar(hp = 5)
        val broadcasts = mutableListOf<ServerMessage>()
        cmd.execute(
            caller,
            "hp Bob 20",
            testContext(sessions = listOf(caller, bob), broadcast = { broadcasts.add(it) }),
        )
        assertTrue(broadcasts.filterIsInstance<ServerMessage.HealthUpdate>().isNotEmpty())
    }

    @Test
    fun setHp_sendsStatusUpdateToTarget() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar(hp = 5)
        cmd.execute(caller, "hp Bob 20", testContext(sessions = listOf(caller, bob)))
        assertTrue(bob.sent.filterIsInstance<ServerMessage.PlayerStatusUpdate>().isNotEmpty())
    }

    @Test
    fun setHp_sendsConfirmationToCaller() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar(hp = 5)
        cmd.execute(caller, "hp Bob 20", testContext(sessions = listOf(caller, bob)))
        assertTrue(caller.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun setHp_savesTargetPlayer() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar(hp = 5)
        val saved = mutableListOf<org.micoli.micraft.session.PlayerSession>()
        cmd.execute(
            caller,
            "hp Bob 20",
            testContext(sessions = listOf(caller, bob), savePlayer = { saved.add(it) }),
        )
        assertTrue(saved.any { it.id == "b" })
    }

    // ── set mana ──────────────────────────────────────────────────────────────

    @Test
    fun setMana_updatesCharacterData() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar(mana = 5)
        cmd.execute(caller, "mana Bob 30", testContext(sessions = listOf(caller, bob)))
        assertNotNull(bob.characterData)
        assertTrue(bob.characterData!!.currentMana > 0)
    }

    @Test
    fun setMana_capsAtMaxMana() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar(mana = 5)
        val maxMana =
            org.micoli.micraft.rpg.character.DerivedStatsCalculator.compute(bob.characterData!!)
                .maxMana
        cmd.execute(caller, "mana Bob 99999", testContext(sessions = listOf(caller, bob)))
        assertEquals(maxMana, bob.characterData!!.currentMana)
    }

    // ── autocomplete ──────────────────────────────────────────────────────────

    @Test
    fun autocomplete_arg0_returnsSubcommands() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val result = cmd.completeArg(0, "", caller, testContext(sessions = listOf(caller)))
        assertTrue("hp" in result)
        assertTrue("mana" in result)
    }

    @Test
    fun autocomplete_arg0_filtersPartial() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val result = cmd.completeArg(0, "h", caller, testContext(sessions = listOf(caller)))
        assertTrue("hp" in result)
        assertTrue("mana" !in result)
    }

    @Test
    fun autocomplete_arg1_returnsPlayerNames() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val bob = sessionWithChar()
        val result = cmd.completeArg(1, "", caller, testContext(sessions = listOf(caller, bob)))
        assertTrue("Bob" in result)
        assertTrue("Alice" in result)
    }

    @Test
    fun autocomplete_arg2_returnsEmpty() = runBlocking {
        val caller = testSession(id = "a", name = "Alice")
        val result = cmd.completeArg(2, "", caller, testContext(sessions = listOf(caller)))
        assertEquals(emptyList(), result)
    }
}
