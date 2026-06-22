package org.micoli.micraft.command

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KickCommandTest {
    private val cmd = KickCommand()

    @Test
    fun blankArgs_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Usage") || it.message.contains("kick") })
    }

    @Test
    fun unknownTarget_sendsError() = runBlocking {
        val session = testSession()
        cmd.execute(session, "Ghost", testContext(sessions = listOf(session)))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("not found") || it.message.contains("Ghost") })
    }

    @Test
    fun validTarget_callsKickSession() = runBlocking {
        val kicked = mutableListOf<String>()
        val target = testSession(id = "target-id", name = "Bob")
        val session = testSession()
        val ctx = testContext(
            sessions = listOf(session, target),
            kickSession = { kicked.add(it) },
        )
        cmd.execute(session, "Bob", ctx)
        assertEquals(listOf("Bob"), kicked)
    }

    @Test
    fun validTarget_notifiesTarget() = runBlocking {
        val target = testSession(id = "target-id", name = "Bob")
        val session = testSession()
        val ctx = testContext(sessions = listOf(session, target))
        cmd.execute(session, "Bob", ctx)
        assertTrue(target.sent.filterIsInstance<ServerMessage.Notification>().any { it.message.contains("kicked") })
    }

    @Test
    fun validTarget_broadcastsToOthers() = runBlocking {
        val bystander = testSession(id = "bystander-id", name = "Carol")
        val target = testSession(id = "target-id", name = "Bob")
        val session = testSession()
        val ctx = testContext(sessions = listOf(session, target, bystander))
        cmd.execute(session, "Bob", ctx)
        assertTrue(bystander.sent.filterIsInstance<ServerMessage.Notification>().any { it.message.contains("Bob") })
    }
}
