package org.micoli.micraft.command

import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RefetchCommandTest {
    private val cmd = RefetchCommand()

    @Test
    fun callsRefetchChunks() = runBlocking {
        val called = mutableListOf<PlayerSession>()
        val session = testSession()
        cmd.execute(session, "", testContext(refetchChunks = { called.add(it) }))
        assertEquals(1, called.size)
        assertEquals(session, called[0])
    }

    @Test
    fun sendsNotificationOnSuccess() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(refetchChunks = {}))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun sendsUnavailableNotificationWhenNoRefetchChunks() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext(refetchChunks = null))
        val notif = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notif.any { it.message.contains("unavailable") || it.message.contains("not available") || it.message.contains("disponible") })
    }
}
