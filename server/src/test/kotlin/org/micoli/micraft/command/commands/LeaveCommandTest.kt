package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.player.ChannelSubscription
import org.micoli.micraft.player.hasChannel
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class LeaveCommandTest {
    private val cmd = LeaveCommand()

    private fun chatCtx(session: FakePlayerSession): CommandContext {
        val mgr = ChatChannelManager()
        val svc = ChatService(mgr, {}, { listOf(session) })
        return testContext(chatChannelManager = mgr, chatService = svc)
    }

    @Test
    fun blankChannel_sendsUsage() = runBlocking {
        val session = testSession()
        cmd.execute(session, "  ", chatCtx(session))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun protectedChannel_sendsCantLeave() = runBlocking {
        val session = testSession()
        session.state =
            session.state.copy(subscribedChannels = listOf(ChannelSubscription("system")))
        cmd.execute(session, "system", chatCtx(session))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("system") ||
                    it.message.contains("cannot") ||
                    it.message.contains("ne peut")
            })
    }

    @Test
    fun notMember_sendsNotMember() = runBlocking {
        val session = testSession()
        cmd.execute(session, "world", chatCtx(session))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("world") ||
                    it.message.contains("not") ||
                    it.message.contains("membre")
            })
    }

    @Test
    fun success_removesFromSubscribedChannels() = runBlocking {
        val session = testSession()
        session.state =
            session.state.copy(subscribedChannels = listOf(ChannelSubscription("world")))
        cmd.execute(session, "world", chatCtx(session))
        assertFalse(session.state.subscribedChannels.hasChannel("world"))
    }

    @Test
    fun success_sendsLeft_notification() = runBlocking {
        val session = testSession()
        session.state =
            session.state.copy(subscribedChannels = listOf(ChannelSubscription("world")))
        cmd.execute(session, "world", chatCtx(session))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("world") ||
                    it.message.contains("left") ||
                    it.message.contains("quitté")
            })
    }

    @Test
    fun completeArg_listsSubscribedChannels() = runBlocking {
        val session = testSession()
        session.state =
            session.state.copy(
                subscribedChannels =
                    listOf(ChannelSubscription("world"), ChannelSubscription("around")))
        val results = cmd.completeArg(0, "w", session, chatCtx(session))
        assertTrue(results.contains("world"))
        assertFalse(results.contains("around"))
    }
}
