package org.micoli.micraft.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testSession

class ChatServiceTest {

    private fun buildService(vararg sessions: org.micoli.micraft.support.FakePlayerSession) =
        ChatService(
            channelManager = ChatChannelManager(),
            savePlayer = {},
            getSessions = { sessions.toList() },
        )

    @Test
    fun `dm message auto-resubscribes player who left channel`() = runBlocking {
        val alice = testSession(id = "a", name = "Alice")
        val bob = testSession(id = "b", name = "Bob")
        val service = buildService(alice, bob)
        val channel = "dm:Alice:Bob"

        // Alice unsubscribed from DM channel
        alice.state =
            alice.state.copy(subscribedChannels = alice.state.subscribedChannels - channel)
        // Bob is subscribed
        bob.state = bob.state.copy(subscribedChannels = bob.state.subscribedChannels + channel)

        // Bob sends a message
        service.routeMessage(bob, channel, "hello")

        // Alice must have received the message
        val aliceMessages = alice.sent.filterIsInstance<ServerMessage.ChatMessage>()
        assertEquals(1, aliceMessages.size)
        assertEquals("hello", aliceMessages[0].message)

        // Alice must be re-subscribed
        assertTrue(channel in alice.state.subscribedChannels)

        // Alice must have received a ChannelsSync
        val aliceSyncs = alice.sent.filterIsInstance<ServerMessage.ChannelsSync>()
        assertEquals(1, aliceSyncs.size)
    }

    @Test
    fun `dm message delivered to already-subscribed players without extra sync`() = runBlocking {
        val alice = testSession(id = "a", name = "Alice")
        val bob = testSession(id = "b", name = "Bob")
        val service = buildService(alice, bob)
        val channel = "dm:Alice:Bob"

        alice.state =
            alice.state.copy(subscribedChannels = alice.state.subscribedChannels + channel)
        bob.state = bob.state.copy(subscribedChannels = bob.state.subscribedChannels + channel)

        service.routeMessage(bob, channel, "hi")

        assertEquals(1, alice.sent.filterIsInstance<ServerMessage.ChatMessage>().size)
        // No spurious ChannelsSync when already subscribed
        assertEquals(0, alice.sent.filterIsInstance<ServerMessage.ChannelsSync>().size)
    }
}
