package org.micoli.micraft.command

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.support.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveCommandTest {
    private val cmd = SaveCommand()

    @Test
    fun callsSavePlayer() = runBlocking {
        val saved = mutableListOf<PlayerSession>()
        val session = testSession()
        cmd.execute(session, "", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
        assertEquals(session, saved[0])
    }

    @Test
    fun sendsNotification() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("saved") || it.message.contains("World") })
    }

    @Test
    fun flushDirty_runsWithoutError_whenNoPersistence() = runBlocking {
        // WorldState with no persistence — flushDirty is a no-op but must not throw
        val session = testSession()
        val world = testWorld()
        cmd.execute(session, "", testContext(world = world))
        // Verify command completed and sent a notification
        assertTrue(session.sent.isNotEmpty())
    }
}
