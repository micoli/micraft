package org.micoli.micraft.command.commands

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession

class TalkCommandTest {
    private val cmd = TalkCommand()

    @Test
    fun blankName_sendsUsage() = runBlocking {
        val session = testSession()
        val mgr = ChatChannelManager()
        val svc = ChatService(mgr, {}, { emptyList() })
        cmd.execute(session, "  ", testContext(chatService = svc, chatChannelManager = mgr))
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun targetNotFound_sendsNotFound() = runBlocking {
        val session = testSession(name = "Alice")
        val mgr = ChatChannelManager()
        val svc = ChatService(mgr, {}, { emptyList() })
        cmd.execute(
            session,
            "Bob",
            testContext(chatService = svc, chatChannelManager = mgr, sessions = listOf(session)))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("Bob") ||
                    it.message.contains("not found") ||
                    it.message.contains("introuvable")
            })
    }

    @Test
    fun success_sendsOpenedNotification() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        val bob = testSession(id = "bob-id", name = "Bob")
        val mgr = ChatChannelManager()
        val svc = ChatService(mgr, {}, { listOf(alice, bob) })
        cmd.execute(
            alice,
            "Bob",
            testContext(chatService = svc, chatChannelManager = mgr, sessions = listOf(alice, bob)))
        val notifs = alice.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("Bob") ||
                    it.message.contains("opened") ||
                    it.message.contains("ouvert")
            })
    }

    @Test
    fun success_createsDmSubscription() = runBlocking {
        val alice = testSession(id = "alice-id", name = "Alice")
        val bob = testSession(id = "bob-id", name = "Bob")
        val mgr = ChatChannelManager()
        val svc = ChatService(mgr, {}, { listOf(alice, bob) })
        cmd.execute(
            alice,
            "Bob",
            testContext(chatService = svc, chatChannelManager = mgr, sessions = listOf(alice, bob)))
        assertTrue(alice.state.subscribedChannels.any { it.name.startsWith("dm:") })
    }

    @Test
    fun completeArg_listsSessions() = runBlocking {
        val session = testSession(name = "Alice")
        val other = testSession(id = "bob-id", name = "Bob")
        val mgr = ChatChannelManager()
        val svc = ChatService(mgr, {}, { listOf(session, other) })
        val ctx = testContext(chatService = svc, sessions = listOf(session, other))
        val results = cmd.completeArg(0, "B", session, ctx)
        assertTrue(results.contains("Bob"))
    }
}
