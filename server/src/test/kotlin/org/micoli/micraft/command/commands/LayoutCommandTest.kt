package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.ui.GameLayout

class LayoutCommandTest {
    private val cmd = LayoutCommand()
    private val layoutsCmd = LayoutsCommand()

    private fun sessionWithLayouts(vararg names: String): FakePlayerSession {
        val session = testSession()
        val layouts = names.map { GameLayout(it, emptyList()) }
        session.state = session.state.copy(layouts = layouts, activeLayout = names.first())
        return session
    }

    @Test
    fun layouts_sendsOpenLayoutEditor() = runBlocking {
        val session = testSession()
        layoutsCmd.execute(session, "", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.OpenLayoutEditor>().isNotEmpty())
    }

    @Test
    fun layout_existingName_switchesActiveLayout() = runBlocking {
        val session = sessionWithLayouts("default", "compact")
        val saved = mutableListOf<PlayerSession>()
        cmd.execute(session, "compact", testContext(savePlayer = { saved.add(it) }))
        assertEquals("compact", session.state.activeLayout)
    }

    @Test
    fun layout_existingName_callsSavePlayer() = runBlocking {
        val session = sessionWithLayouts("default", "compact")
        val saved = mutableListOf<PlayerSession>()
        cmd.execute(session, "compact", testContext(savePlayer = { saved.add(it) }))
        assertEquals(1, saved.size)
    }

    @Test
    fun layout_existingName_sendsLayoutsSync() = runBlocking {
        val session = sessionWithLayouts("default", "compact")
        cmd.execute(session, "compact", testContext())
        assertTrue(session.sent.filterIsInstance<ServerMessage.LayoutsSync>().isNotEmpty())
    }

    @Test
    fun layout_unknownName_sendsNotification() = runBlocking {
        val session = sessionWithLayouts("default")
        cmd.execute(session, "nonexistent", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("nonexistent") ||
                    it.message.contains("not found") ||
                    it.message.contains("introuvable")
            })
    }

    @Test
    fun layout_unknownName_doesNotSave() = runBlocking {
        val session = sessionWithLayouts("default")
        val saved = mutableListOf<PlayerSession>()
        cmd.execute(session, "nonexistent", testContext(savePlayer = { saved.add(it) }))
        assertEquals(0, saved.size)
    }

    @Test
    fun layout_noArgs_sendsUsageHint() = runBlocking {
        val session = testSession()
        cmd.execute(session, "", testContext())
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun layout_unknownName_stateUnchanged() = runBlocking {
        val session = sessionWithLayouts("default")
        cmd.execute(session, "other", testContext())
        assertEquals("default", session.state.activeLayout)
    }
}
