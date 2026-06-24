package org.micoli.micraft.tick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.support.testSession
import org.micoli.micraft.world.ChatChannelManager

class PreferencesUpdateTest {

    private suspend fun handle(session: PlayerSession, msg: ClientMessage.PreferencesUpdate) {
        val knownChannels = ChatChannelManager.BUILTIN.sorted()
        val newSubscribed =
            (msg.subscribedChannels.filter { it in knownChannels } + ChatChannelManager.PROTECTED)
                .distinct()
        val shadersChanged = session.state.shadersEnabled != msg.shadersEnabled
        session.state =
            session.state.copy(
                subscribedChannels = newSubscribed,
                disabledCommands = msg.disabledCommands,
                shadersEnabled = msg.shadersEnabled,
            )
        session.send(ServerMessage.ChannelsSync(newSubscribed, knownChannels))
        if (shadersChanged) session.send(ServerMessage.ShadersUpdate(msg.shadersEnabled))
    }

    @Test
    fun updatesSubscribedChannels() = runBlocking {
        val session = testSession()
        handle(
            session, ClientMessage.PreferencesUpdate(listOf("world", "around"), emptySet(), true))
        assertTrue("world" in session.state.subscribedChannels)
        assertTrue("around" in session.state.subscribedChannels)
    }

    @Test
    fun protectedChannelsAlwaysKept() = runBlocking {
        val session = testSession()
        handle(session, ClientMessage.PreferencesUpdate(emptyList(), emptySet(), true))
        assertTrue("system" in session.state.subscribedChannels)
        assertTrue("game" in session.state.subscribedChannels)
    }

    @Test
    fun unknownChannelIgnored() = runBlocking {
        val session = testSession()
        handle(
            session, ClientMessage.PreferencesUpdate(listOf("unknown_channel"), emptySet(), true))
        assertFalse("unknown_channel" in session.state.subscribedChannels)
    }

    @Test
    fun updatesDisabledCommands() = runBlocking {
        val session = testSession()
        val uuid = "b2a1d2bb-1912-4ca2-8b60-8b2012b2ab30"
        handle(session, ClientMessage.PreferencesUpdate(listOf("world"), setOf(uuid), true))
        assertTrue(uuid in session.state.disabledCommands)
    }

    @Test
    fun shadersChangeSendsShadersUpdate() = runBlocking {
        val session = testSession(shadersEnabled = true)
        handle(session, ClientMessage.PreferencesUpdate(listOf("world"), emptySet(), false))
        assertFalse(session.state.shadersEnabled)
        val updates = session.sent.filterIsInstance<ServerMessage.ShadersUpdate>()
        assertEquals(1, updates.size)
        assertFalse(updates[0].enabled)
    }

    @Test
    fun noShadersChangeNoShadersUpdate() = runBlocking {
        val session = testSession(shadersEnabled = true)
        handle(session, ClientMessage.PreferencesUpdate(listOf("world"), emptySet(), true))
        val updates = session.sent.filterIsInstance<ServerMessage.ShadersUpdate>()
        assertTrue(updates.isEmpty())
    }

    @Test
    fun alwaysSendsChannelsSync() = runBlocking {
        val session = testSession()
        handle(session, ClientMessage.PreferencesUpdate(listOf("world"), emptySet(), true))
        val syncs = session.sent.filterIsInstance<ServerMessage.ChannelsSync>()
        assertEquals(1, syncs.size)
    }
}
