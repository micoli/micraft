package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class ReloadCommandTest {
    private val cmd = ReloadCommand()

    @Test
    fun noReloadConfig_sendsUnavailable() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(reloadConfig = null))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notif.any {
                it.message.contains("not available") ||
                    it.message.contains("unavailable") ||
                    it.message.contains("Reload")
            })
    }

    @Test
    fun withReloadConfig_invokesCallback() = runBlocking {
        var callCount = 0
        val session = testSession()
        val ctx =
            testContext(
                reloadConfig = { _ ->
                    callCount++
                    "3 types reloaded"
                })
        cmd.execute(session, "", ctx)
        assertEquals(1, callCount)
    }

    @Test
    fun withReloadConfig_sendsResult() = runBlocking {
        val session = testSession()
        val ctx = testContext(reloadConfig = { _ -> "5 types reloaded" })
        cmd.execute(session, "", ctx)
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("5 types reloaded") })
    }
}
