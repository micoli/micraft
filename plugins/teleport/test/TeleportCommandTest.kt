package org.micoli.micraft.plugins.teleport

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TeleportCommandTest {
    private val cmd = TeleportCommand()

    @Test
    fun byCoords_setsPos() = runBlocking {
        val session = testSession()
        cmd.execute(session, "10 20 30", testContext())
        assertEquals(10f, session.state.pos.x)
        assertEquals(30f, session.state.pos.z)
    }

    @Test
    fun byCoords_resetsVy() = runBlocking {
        val session = testSession()
        session.vy = 5f
        cmd.execute(session, "10 20 30", testContext())
        assertEquals(0f, session.vy)
    }

    @Test
    fun byCoords_sendsPlayerUpdateAndNotification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "10 20 30", testContext())
        assertIs<ServerMessage.PlayerUpdate>(session.sent.first { it is ServerMessage.PlayerUpdate })
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Teleported") })
    }

    @Test
    fun commaDelimited_parsesCorrectly() = runBlocking {
        val session = testSession()
        cmd.execute(session, "5,10,15", testContext())
        assertEquals(5f, session.state.pos.x)
        assertEquals(15f, session.state.pos.z)
    }

    @Test
    fun badArgs_sendsUsageError() = runBlocking {
        val session = testSession()
        cmd.execute(session, "notanumber", testContext(sessions = emptyList()))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Usage") || it.message.contains("not found") || it.message.contains("teleport") })
    }

    @Test
    fun toPlayer_setsPos() = runBlocking {
        val target = testSession(id = "bob-id", name = "Bob", pos = Vec3(50f, 10f, 50f))
        val session = testSession()
        cmd.execute(session, "Bob", testContext(sessions = listOf(session, target)))
        assertEquals(50f, session.state.pos.x)
        assertEquals(50f, session.state.pos.z)
    }

    @Test
    fun toPlayer_notFound_sendsError() = runBlocking {
        val session = testSession()
        cmd.execute(session, "Bob", testContext(sessions = listOf(session)))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("not found") || it.message.contains("Bob") })
    }

    @Test
    fun toPlayer_resetsVy() = runBlocking {
        val target = testSession(id = "bob-id", name = "Bob", pos = Vec3(50f, 10f, 50f))
        val session = testSession()
        session.vy = 3f
        cmd.execute(session, "Bob", testContext(sessions = listOf(session, target)))
        assertEquals(0f, session.vy)
    }
}
