package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.EditMode
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class ModeCommandTest {
    private val mode = ModeCommand()

    @Test
    fun `creative switches editMode`() = runBlocking {
        val session = testSession()
        mode.execute(session, "creative", testContext())
        assertEquals(EditMode.CREATIVE, session.state.editMode)
    }

    @Test
    fun `game switches editMode back`() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(editMode = EditMode.CREATIVE)
        mode.execute(session, "game", testContext())
        assertEquals(EditMode.GAME, session.state.editMode)
    }

    @Test
    fun `creative sends EditModeUpdate`() = runBlocking {
        val session = testSession()
        mode.execute(session, "creative", testContext())
        val updates = session.sent.filterIsInstance<ServerMessage.EditModeUpdate>()
        assertEquals(1, updates.size)
        assertEquals(EditMode.CREATIVE, updates.first().mode)
    }

    @Test
    fun `invalid arg rejected without state change`() = runBlocking {
        val session = testSession()
        mode.execute(session, "flying", testContext())
        assertEquals(EditMode.GAME, session.state.editMode)
        assertTrue(session.sent.filterIsInstance<ServerMessage.EditModeUpdate>().isEmpty())
    }

    @Test
    fun `saves player`() = runBlocking {
        val saved = mutableListOf<PlayerSession>()
        val session = testSession()
        mode.execute(session, "creative", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }
}
