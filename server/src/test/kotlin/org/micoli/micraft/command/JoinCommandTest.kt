package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.world.ChatChannelManager
import org.micoli.micraft.world.ChatService

class JoinCommandTest {
    private val cmd = JoinCommand()

    private fun chatCtx(
        session: org.micoli.micraft.support.FakePlayerSession
    ): org.micoli.micraft.CommandContext {
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
    fun unknownChannel_sendsNotFound() = runBlocking {
        val session = testSession()
        cmd.execute(session, "nonexistent", chatCtx(session))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("nonexistent") ||
                    it.message.contains("not found") ||
                    it.message.contains("introuvable")
            })
    }

    @Test
    fun alreadyMember_sendsAlreadyMember() = runBlocking {
        val session = testSession()
        session.state = session.state.copy(subscribedChannels = listOf("world"))
        cmd.execute(session, "world", chatCtx(session))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("world") ||
                    it.message.contains("already") ||
                    it.message.contains("déjà")
            })
    }

    @Test
    fun success_sendsJoined() = runBlocking {
        val session = testSession()
        cmd.execute(session, "world", chatCtx(session))
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("world") ||
                    it.message.contains("joined") ||
                    it.message.contains("rejoint")
            })
    }

    @Test
    fun success_subscribesSession() = runBlocking {
        val session = testSession()
        cmd.execute(session, "world", chatCtx(session))
        assertTrue("world" in session.state.subscribedChannels)
    }

    @Test
    fun completeArg_listsKnownChannels() = runBlocking {
        val session = testSession()
        val mgr = ChatChannelManager()
        val svc = ChatService(mgr, {}, { listOf(session) })
        val ctx = testContext(chatChannelManager = mgr, chatService = svc)
        val results = cmd.completeArg(0, "w", session, ctx)
        assertTrue(results.any { it.startsWith("w", ignoreCase = true) })
    }
}
