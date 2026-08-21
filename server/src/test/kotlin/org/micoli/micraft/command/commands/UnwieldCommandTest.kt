package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class UnwieldCommandTest {
    private val cmd = UnwieldCommand()

    @Test
    fun invalidHand_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "up", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun emptyHand_sendsEmpty() = runBlocking {
        val session = testSession()
        cmd.execute(session, "right", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun success_clearsHandAndSaves() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(rightHandItem = "iron_sword")
        var saved = 0
        val broadcasts = mutableListOf<ServerMessage>()
        cmd.execute(
            session,
            "right",
            testContext(savePlayer = { saved++ }, broadcast = { broadcasts.add(it) }))
        assertNull(session.state.rightHandItem)
        assertEquals(1, saved)
        assertTrue(broadcasts.filterIsInstance<ServerMessage.PlayerUpdate>().isNotEmpty())
    }
}
