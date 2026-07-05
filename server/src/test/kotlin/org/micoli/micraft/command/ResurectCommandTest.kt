package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.combat.CombatState
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class ResurectCommandTest {

    private val cmd = ResurectCommand()

    private fun downedSession(id: String = "b", name: String = "Bob") =
        testSession(id = id, name = name).also { s ->
            s.characterData =
                CharacterData(
                    id = id,
                    name = name,
                    characterClass = CharacterClass.WARRIOR,
                    baseStats = BaseStats(),
                    currentHp = 0,
                    currentMana = 0,
                )
            s.combatState = CombatState(isDowned = true)
        }

    // ── target resolution ─────────────────────────────────────────────────────

    @Test
    fun unknownTarget_sendsNotFound() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        cmd.execute(caster, "Nobody", testContext(sessions = listOf(caster)))
        val notifs = caster.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun targetNotDowned_sendsNotDowned() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val bob = testSession(id = "b", name = "Bob")
        cmd.execute(caster, "Bob", testContext(sessions = listOf(caster, bob)))
        val notifs = caster.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun targetNoCharacterData_sendsError() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val bob =
            testSession(id = "b", name = "Bob").also {
                it.combatState = CombatState(isDowned = true)
            }
        cmd.execute(caster, "Bob", testContext(sessions = listOf(caster, bob)))
        val notifs = caster.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    // ── successful resurect ───────────────────────────────────────────────────

    @Test
    fun resurect_clearsDownedState() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val bob = downedSession()
        cmd.execute(caster, "Bob", testContext(sessions = listOf(caster, bob)))
        assertFalse(bob.combatState.isDowned)
    }

    @Test
    fun resurect_restoresHp() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val bob = downedSession()
        cmd.execute(caster, "Bob", testContext(sessions = listOf(caster, bob)))
        assertTrue(bob.characterData!!.currentHp > 0)
    }

    @Test
    fun resurect_broadcastsPlayerRespawned() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val bob = downedSession()
        val broadcasts = mutableListOf<ServerMessage>()
        cmd.execute(
            caster,
            "Bob",
            testContext(
                sessions = listOf(caster, bob),
                broadcast = { broadcasts.add(it) },
            ),
        )
        // PlayerRespawned is sent per-session via sessions().forEach
        val respawned = (caster.sent + bob.sent).filterIsInstance<ServerMessage.PlayerRespawned>()
        assertTrue(respawned.any { it.playerId == "b" })
    }

    @Test
    fun resurect_savesTarget() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val bob = downedSession()
        val saved = mutableListOf<org.micoli.micraft.session.PlayerSession>()
        cmd.execute(
            caster,
            "Bob",
            testContext(sessions = listOf(caster, bob), savePlayer = { saved.add(it) }),
        )
        assertTrue(saved.any { it.id == "b" })
    }

    @Test
    fun resurect_otherPlayer_sendsDoneNotificationToCaster() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val bob = downedSession()
        cmd.execute(caster, "Bob", testContext(sessions = listOf(caster, bob)))
        val notifs = caster.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun resurect_self_noNotificationToCaster() = runBlocking {
        val caster = downedSession(id = "a", name = "Alice")
        cmd.execute(caster, "", testContext(sessions = listOf(caster)))
        // No "done" notification when self-resurect
        val notifs = caster.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.none {
                it.message.contains("Alice", ignoreCase = true) &&
                    it.message.contains("resur", ignoreCase = true)
            })
    }

    // ── autocomplete ──────────────────────────────────────────────────────────

    @Test
    fun autocomplete_returnsDownedPlayerNames() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val bob = downedSession(id = "b", name = "Bob")
        val carol = testSession(id = "c", name = "Carol") // not downed
        val result =
            cmd.completeArg(0, "", caster, testContext(sessions = listOf(caster, bob, carol)))
        assertTrue("Bob" in result)
        assertFalse("Carol" in result)
    }

    @Test
    fun autocomplete_filtersOnPartial() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val bob = downedSession(id = "b", name = "Bob")
        val barry = downedSession(id = "d", name = "Barry")
        val result =
            cmd.completeArg(0, "Ba", caster, testContext(sessions = listOf(caster, bob, barry)))
        assertTrue("Barry" in result)
        assertFalse("Bob" in result)
    }

    @Test
    fun autocomplete_wrongArgIndex_returnsEmpty() = runBlocking {
        val caster = testSession(id = "a", name = "Alice")
        val result = cmd.completeArg(1, "", caster, testContext(sessions = listOf(caster)))
        assertEquals(emptyList(), result)
    }
}
