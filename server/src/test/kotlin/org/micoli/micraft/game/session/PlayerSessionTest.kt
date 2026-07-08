package org.micoli.micraft.game.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.support.FakeWebSocketSession
import org.micoli.micraft.support.testPlayerState
import org.micoli.micraft.support.testSession

class PlayerSessionTest {
    private fun sessionWithPerms(perms: Set<String>): PlayerSession =
        PlayerSession(
            id = "test-id",
            userName = "alice",
            socket = FakeWebSocketSession(),
            state = testPlayerState(),
            permissions = perms,
        )

    @Test
    fun hasPermission_wildcardGrantsAll() {
        val session = sessionWithPerms(setOf("*"))
        assertTrue(session.hasPermission("action.fly"))
        assertTrue(session.hasPermission("admin.kick"))
        assertTrue(session.hasPermission("anything"))
    }

    @Test
    fun hasPermission_specificPerm_onlyThatPerm() {
        val session = sessionWithPerms(setOf("action.fly"))
        assertTrue(session.hasPermission("action.fly"))
        assertFalse(session.hasPermission("action.break"))
        assertFalse(session.hasPermission("admin.kick"))
    }

    @Test
    fun hasPermission_emptyPerms_returnsFalse() {
        val session = sessionWithPerms(emptySet())
        assertFalse(session.hasPermission("anything"))
    }

    @Test
    fun inventory_initiallyEmpty() {
        val session = testSession()
        assertTrue(session.inventory.isEmpty())
    }

    @Test
    fun actionHistory_initiallyEmpty() {
        val session = testSession()
        assertTrue(session.actionHistory.isEmpty())
    }

    @Test
    fun loadedChunks_initiallyEmpty() {
        val session = testSession()
        assertTrue(session.loadedChunks.isEmpty())
    }

    @Test
    fun intents_canReceiveAndDrain() =
        runBlocking<Unit> {
            val session = testSession()
            val intent = ClientMessage.Command("/help")
            session.intents.send(intent)
            val received = session.intents.tryReceive().getOrNull()
            assertEquals(intent, received)
        }

    @Test
    fun shortcutBar_initiallyAllNull() {
        val session = testSession()
        assertTrue(session.shortcutBar.all { it == null })
        assertEquals(10, session.shortcutBar.size)
    }
}
