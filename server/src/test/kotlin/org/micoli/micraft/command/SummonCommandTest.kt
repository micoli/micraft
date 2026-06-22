package org.micoli.micraft.command

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummonCommandTest {
    private val cmd = SummonCommand()

    @Test
    fun blankArgs_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Usage") || it.message.contains("summon") })
    }

    @Test
    fun unknownTarget_sendsError() = runBlocking {
        val session = testSession()
        cmd.execute(session, "Ghost", testContext(sessions = listOf(session)))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("not found") || it.message.contains("Ghost") })
    }

    @Test
    fun validTarget_updatesTargetPos() = runBlocking {
        val caller = testSession(pos = Vec3(100f, 10f, 200f))
        val target = testSession(id = "bob-id", name = "Bob", pos = Vec3(0f, 0f, 0f))
        cmd.execute(caller, "Bob", testContext(sessions = listOf(caller, target)))
        assertEquals(100f, target.state.pos.x)
        assertEquals(200f, target.state.pos.z)
    }

    @Test
    fun validTarget_resetsTargetVy() = runBlocking {
        val caller = testSession(pos = Vec3(100f, 10f, 200f))
        val target = testSession(id = "bob-id", name = "Bob")
        target.vy = 5f
        cmd.execute(caller, "Bob", testContext(sessions = listOf(caller, target)))
        assertEquals(0f, target.vy)
    }

    @Test
    fun validTarget_notifiesBothSides() = runBlocking {
        val caller = testSession(pos = Vec3(100f, 10f, 200f))
        val target = testSession(id = "bob-id", name = "Bob")
        cmd.execute(caller, "Bob", testContext(sessions = listOf(caller, target)))
        // Target gets PlayerUpdate and summoned notification
        assertTrue(target.sent.any { it is ServerMessage.PlayerUpdate })
        assertTrue(target.sent.filterIsInstance<ServerMessage.Notification>().any { it.message.contains("summoned") || it.message.contains("Alice") })
        // Caller gets done notification
        assertTrue(caller.sent.filterIsInstance<ServerMessage.Notification>().any { it.message.contains("Bob") })
    }
}
