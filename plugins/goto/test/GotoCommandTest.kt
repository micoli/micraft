package org.micoli.micraft.plugins.goto

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GotoCommandTest {
    private val cmd = GotoCommand()

    @Test
    fun blankArgs_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Usage") || it.message.contains("goto") })
    }

    @Test
    fun unknownPlayer_sendsError() = runBlocking {
        val session = testSession()
        cmd.execute(session, "Ghost", testContext(sessions = listOf(session)))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("not found") || it.message.contains("Ghost") })
    }

    @Test
    fun validPlayer_updatesCallerPos() = runBlocking {
        val target = testSession(id = "target-id", name = "Bob", pos = Vec3(100f, 10f, 200f))
        val session = testSession()
        cmd.execute(session, "Bob", testContext(sessions = listOf(session, target)))
        assertEquals(100f, session.state.pos.x)
        assertEquals(200f, session.state.pos.z)
    }

    @Test
    fun validPlayer_resetsVy() = runBlocking {
        val target = testSession(id = "target-id", name = "Bob", pos = Vec3(100f, 10f, 200f))
        val session = testSession()
        session.vy = 5f
        cmd.execute(session, "Bob", testContext(sessions = listOf(session, target)))
        assertEquals(0f, session.vy)
    }

    @Test
    fun validPlayer_sendsPlayerUpdateAndNotification() = runBlocking {
        val target = testSession(id = "target-id", name = "Bob", pos = Vec3(100f, 10f, 200f))
        val session = testSession()
        cmd.execute(session, "Bob", testContext(sessions = listOf(session, target)))
        assertTrue(session.sent.any { it is ServerMessage.PlayerUpdate })
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().any { it.message.contains("Bob") })
    }
}
