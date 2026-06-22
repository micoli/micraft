package org.micoli.micraft.command

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YieldCommandTest {
    private val cmd = YieldCommand()

    @Test
    fun blankMessage_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("Usage") || it.message.contains("yield") })
    }

    @Test
    fun message_broadcastsToAll() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val session = testSession(name = "Alice")
        cmd.execute(session, "Hello World", testContext(broadcast = { broadcasts.add(it) }))
        val notif = broadcasts.filterIsInstance<ServerMessage.Notification>()
        assertEquals(1, notif.size)
        assertTrue(notif[0].message.contains("Alice"))
        assertTrue(notif[0].message.contains("Hello World"))
    }

    @Test
    fun message_doesNotSendToCallerOnly() = runBlocking {
        val broadcasts = mutableListOf<ServerMessage>()
        val session = testSession()
        cmd.execute(session, "test", testContext(broadcast = { broadcasts.add(it) }))
        // broadcast is called, not session.send directly
        assertEquals(0, session.sent.size)
        assertEquals(1, broadcasts.size)
    }
}
