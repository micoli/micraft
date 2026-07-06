package org.micoli.micraft.tick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.player.ChannelSubscription
import org.micoli.micraft.player.hasChannel
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.support.testSession
import org.micoli.micraft.world.ChatChannelManager

class PreferencesUpdateTest {

    private suspend fun handle(session: PlayerSession, msg: ClientMessage.PreferencesUpdate) {
        val knownChannels = ChatChannelManager.BUILTIN.sorted()
        val newSubscribed =
            (msg.subscribedChannels.filter { it.name in knownChannels } +
                    ChatChannelManager.PROTECTED.map { ChannelSubscription(it) })
                .distinctBy { it.name }
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
            session,
            ClientMessage.PreferencesUpdate(
                listOf(ChannelSubscription("world"), ChannelSubscription("around")),
                emptySet(),
                true))
        assertTrue(session.state.subscribedChannels.hasChannel("world"))
        assertTrue(session.state.subscribedChannels.hasChannel("around"))
    }

    @Test
    fun protectedChannelsAlwaysKept() = runBlocking {
        val session = testSession()
        handle(session, ClientMessage.PreferencesUpdate(emptyList(), emptySet(), true))
        assertTrue(session.state.subscribedChannels.hasChannel("system"))
        assertTrue(session.state.subscribedChannels.hasChannel("game"))
    }

    @Test
    fun unknownChannelIgnored() = runBlocking {
        val session = testSession()
        handle(
            session,
            ClientMessage.PreferencesUpdate(
                listOf(ChannelSubscription("unknown_channel")), emptySet(), true))
        assertFalse(session.state.subscribedChannels.hasChannel("unknown_channel"))
    }

    @Test
    fun updatesDisabledCommands() = runBlocking {
        val session = testSession()
        val uuid = "b2a1d2bb-1912-4ca2-8b60-8b2012b2ab30"
        handle(
            session,
            ClientMessage.PreferencesUpdate(
                listOf(ChannelSubscription("world")), setOf(uuid), true))
        assertTrue(uuid in session.state.disabledCommands)
    }

    @Test
    fun shadersChangeSendsShadersUpdate() = runBlocking {
        val session = testSession(shadersEnabled = true)
        handle(
            session,
            ClientMessage.PreferencesUpdate(
                listOf(ChannelSubscription("world")), emptySet(), false))
        assertFalse(session.state.shadersEnabled)
        val updates = session.sent.filterIsInstance<ServerMessage.ShadersUpdate>()
        assertEquals(1, updates.size)
        assertFalse(updates[0].enabled)
    }

    @Test
    fun noShadersChangeNoShadersUpdate() = runBlocking {
        val session = testSession(shadersEnabled = true)
        handle(
            session,
            ClientMessage.PreferencesUpdate(listOf(ChannelSubscription("world")), emptySet(), true))
        val updates = session.sent.filterIsInstance<ServerMessage.ShadersUpdate>()
        assertTrue(updates.isEmpty())
    }

    @Test
    fun autoFocusFlagIsKeptForSubscribedChannel() = runBlocking {
        val session = testSession()
        handle(
            session,
            ClientMessage.PreferencesUpdate(
                listOf(ChannelSubscription("world", autoFocus = true)), emptySet(), true))
        assertTrue(session.state.subscribedChannels.any { it.name == "world" && it.autoFocus })
        val syncs = session.sent.filterIsInstance<ServerMessage.ChannelsSync>()
        assertTrue(syncs.single().subscribedChannels.any { it.name == "world" && it.autoFocus })
    }

    @Test
    fun forcedProtectedChannelDoesNotOverrideClientAutoFocus() = runBlocking {
        val session = testSession()
        handle(
            session,
            ClientMessage.PreferencesUpdate(
                listOf(ChannelSubscription("system", autoFocus = true)), emptySet(), true))
        assertTrue(session.state.subscribedChannels.any { it.name == "system" && it.autoFocus })
    }

    @Test
    fun alwaysSendsChannelsSync() = runBlocking {
        val session = testSession()
        handle(
            session,
            ClientMessage.PreferencesUpdate(listOf(ChannelSubscription("world")), emptySet(), true))
        val syncs = session.sent.filterIsInstance<ServerMessage.ChannelsSync>()
        assertEquals(1, syncs.size)
    }
}
