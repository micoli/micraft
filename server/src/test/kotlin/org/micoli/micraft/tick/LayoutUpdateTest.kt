package org.micoli.micraft.tick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.support.testSession
import org.micoli.micraft.ui.GameLayout
import org.micoli.micraft.ui.defaultLayout
import org.micoli.micraft.ui.validateLayouts

class LayoutUpdateTest {

    private suspend fun handle(session: PlayerSession, msg: ClientMessage.LayoutUpdate) {
        val error = validateLayouts(msg.layouts, msg.activeLayout)
        if (error != null) {
            session.send(ServerMessage.Notification(error))
        } else {
            session.state =
                session.state.copy(layouts = msg.layouts, activeLayout = msg.activeLayout)
        }
    }

    @Test
    fun validLayouts_updatesState() = runBlocking {
        val session = testSession()
        val layouts = listOf(defaultLayout(), GameLayout("compact", emptyList()))
        handle(session, ClientMessage.LayoutUpdate(layouts, "compact"))
        assertEquals("compact", session.state.activeLayout)
        assertEquals(2, session.state.layouts.size)
    }

    @Test
    fun duplicateNames_rejectsWithNotification() = runBlocking {
        val session = testSession()
        val originalLayouts = session.state.layouts
        val layouts = listOf(GameLayout("a", emptyList()), GameLayout("a", emptyList()))
        handle(session, ClientMessage.LayoutUpdate(layouts, "a"))
        assertEquals(originalLayouts, session.state.layouts)
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun emptyLayouts_rejectsWithNotification() = runBlocking {
        val session = testSession()
        handle(session, ClientMessage.LayoutUpdate(emptyList(), "default"))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
    }

    @Test
    fun activeLayoutNotInList_rejectsWithNotification() = runBlocking {
        val session = testSession()
        val layouts = listOf(defaultLayout())
        handle(session, ClientMessage.LayoutUpdate(layouts, "nonexistent"))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isNotEmpty())
        assertEquals("default", session.state.activeLayout)
    }

    @Test
    fun validLayouts_noNotificationSent() = runBlocking {
        val session = testSession()
        val layouts = listOf(defaultLayout())
        handle(session, ClientMessage.LayoutUpdate(layouts, "default"))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(notifs.isEmpty())
    }
}
