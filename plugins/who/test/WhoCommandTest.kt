package org.micoli.micraft.plugins.who

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import kotlin.test.Test
import kotlin.test.assertTrue

class WhoCommandTest {
    private val cmd = WhoCommand()

    @Test
    fun noSessions_sendsEmptyMessage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(sessions = emptyList()))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("No players") || it.message.contains("empty") })
    }

    @Test
    fun oneSession_containsNameAndCoords() = runBlocking {
        val player = testSession(name = "Charlie", pos = Vec3(10f, 20f, 30f))
        val session = testSession()
        cmd.execute(session, "", testContext(sessions = listOf(player)))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("Charlie"))
        assertTrue(notif.message.contains("10"))
        assertTrue(notif.message.contains("30"))
    }

    @Test
    fun multiSession_containsBoth() = runBlocking {
        val alice = testSession(name = "Alice", pos = Vec3(1f, 1f, 1f))
        val bob = testSession(id = "bob-id", name = "Bob", pos = Vec3(2f, 2f, 2f))
        val session = testSession(id = "admin-id", name = "Admin")
        cmd.execute(session, "", testContext(sessions = listOf(alice, bob)))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>().first()
        assertTrue(notif.message.contains("Alice"))
        assertTrue(notif.message.contains("Bob"))
    }
}
