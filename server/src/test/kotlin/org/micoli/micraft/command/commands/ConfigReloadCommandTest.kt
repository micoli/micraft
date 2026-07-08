package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class ConfigReloadCommandTest {
    private val cmd = ConfigReloadCommand()

    @Test
    fun noArg_reloadsBoth() = runBlocking {
        var blocks = 0
        var npcs = 0
        val session = testSession()
        cmd.execute(
            session,
            "",
            testContext(reloadBlocks = { blocks++ }, reloadNpcs = { npcs++ }),
        )
        assertEquals(1, blocks)
        assertEquals(1, npcs)
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("blocks") && it.message.contains("npcs") })
    }

    @Test
    fun blockArg_reloadsOnlyBlocks() = runBlocking {
        var blocks = 0
        var npcs = 0
        val session = testSession()
        cmd.execute(
            session,
            "block",
            testContext(reloadBlocks = { blocks++ }, reloadNpcs = { npcs++ }),
        )
        assertEquals(1, blocks)
        assertEquals(0, npcs)
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("blocks") })
    }

    @Test
    fun npcArg_reloadsOnlyNpcs() = runBlocking {
        var blocks = 0
        var npcs = 0
        val session = testSession()
        cmd.execute(
            session,
            "npc",
            testContext(reloadBlocks = { blocks++ }, reloadNpcs = { npcs++ }),
        )
        assertEquals(0, blocks)
        assertEquals(1, npcs)
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("npcs") })
    }

    @Test
    fun unknownArg_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "banana", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Usage") || it.message.contains("usage") })
    }

    @Test
    fun reloadBlocks_unavailable_sendsUnavailable() = runBlocking {
        val session = testSession()
        cmd.execute(session, "block", testContext(reloadBlocks = null))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("blocks") })
    }

    @Test
    fun autocomplete_arg0_returnsOptions() = runBlocking {
        val result = cmd.completeArg(0, "", testSession(), testContext())
        assertTrue(result.containsAll(listOf("block", "npc")))
    }

    @Test
    fun autocomplete_arg0_partial_filtersOptions() = runBlocking {
        val result = cmd.completeArg(0, "b", testSession(), testContext())
        assertTrue(result.contains("block"))
        assertTrue(result.none { it == "npc" })
    }
}
