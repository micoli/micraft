package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class SkinCommandTest {
    private val cmd = SkinCommand()

    @Test
    fun blankArgs_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "  ", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun unknownSkin_sendsUnknown() = runBlocking {
        val session = testSession()
        cmd.execute(session, "dragonscale", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("dragonscale") ||
                    it.message.contains("unknown") ||
                    it.message.contains("inconnu")
            })
    }

    @Test
    fun sameSkin_sendsAlready() = runBlocking {
        val session = testSession()
        // default skin is "player"; availablePlayerSkins() returns ["player"] in test env
        session.state = session.state.copy(skin = "player")
        cmd.execute(session, "player", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("player") ||
                    it.message.contains("already") ||
                    it.message.contains("déjà")
            })
    }

    @Test
    fun completeArg_returnsAvailableSkins() = runBlocking {
        val results = cmd.completeArg(0, "", null, testContext())
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun completeArg_filtersOnPartial() = runBlocking {
        val results = cmd.completeArg(0, "pl", null, testContext())
        assertTrue(results.all { it.startsWith("pl", ignoreCase = true) })
    }
}
