package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class GodCommandTest {
    private val godOn = GodOnCommand()
    private val godOff = GodOffCommand()

    @Test
    fun `godOn enables godMode`() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(godMode = false)
        godOn.execute(session, "", testContext())
        assertTrue(session.state.godMode)
    }

    @Test
    fun `godOn sends notification`() = runBlocking {
        val session = testSession()
        godOn.execute(session, "", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun `godOn saves player`() = runBlocking {
        val saved = mutableListOf<PlayerSession>()
        val session = testSession()
        godOn.execute(session, "", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }

    @Test
    fun `godOff disables godMode`() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(godMode = true)
        godOff.execute(session, "", testContext())
        assertFalse(session.state.godMode)
    }

    @Test
    fun `godOff sends notification`() = runBlocking {
        val session = testSession()
        godOff.execute(session, "", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun `godOff saves player`() = runBlocking {
        val saved = mutableListOf<PlayerSession>()
        val session = testSession()
        godOff.execute(session, "", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }
}
