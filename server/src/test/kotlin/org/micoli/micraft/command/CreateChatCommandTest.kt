package org.micoli.micraft.command

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testContext
import org.micoli.micraft.support.testSession
import org.micoli.micraft.world.ChatChannelManager
import org.micoli.micraft.world.ChatService

class CreateChatCommandTest {
    private val cmd = CreateChatCommand()

    private fun chatContext(
        saved: MutableList<org.micoli.micraft.session.PlayerSession> = mutableListOf()
    ): Pair<ChatChannelManager, org.micoli.micraft.CommandContext> {
        val mgr = ChatChannelManager()
        val session = testSession()
        val svc = ChatService(mgr, { saved.add(it) }, { listOf(session) })
        return mgr to testContext(chatChannelManager = mgr, chatService = svc)
    }

    @Test
    fun blankName_sendsUsage() = runBlocking {
        val session = testSession()
        val (_, ctx) = chatContext()
        cmd.execute(session, "  ", ctx)
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun builtinName_sendsReserved() = runBlocking {
        val session = testSession()
        val (_, ctx) = chatContext()
        cmd.execute(session, "world", ctx)
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("world") ||
                    it.message.contains("reserved") ||
                    it.message.contains("réservé")
            })
    }

    @Test
    fun dmPrefix_sendsReserved() = runBlocking {
        val session = testSession()
        val (_, ctx) = chatContext()
        cmd.execute(session, "dm:alice", ctx)
        assertTrue(session.sent.filterIsInstance<ServerMessage.Notification>().isNotEmpty())
    }

    @Test
    fun existingCustomChannel_sendsExists() = runBlocking {
        val session = testSession()
        val (mgr, ctx) = chatContext()
        mgr.registerChannel("mychannel")
        cmd.execute(session, "mychannel", ctx)
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("mychannel") ||
                    it.message.contains("exist") ||
                    it.message.contains("existe")
            })
    }

    @Test
    fun newChannel_sendsCreated() = runBlocking {
        val session = testSession()
        val (_, ctx) = chatContext()
        cmd.execute(session, "newchan", ctx)
        val notifs = session.sent.filterIsInstance<ServerMessage.Notification>()
        assertTrue(
            notifs.any {
                it.message.contains("newchan") ||
                    it.message.contains("created") ||
                    it.message.contains("créé")
            })
    }

    @Test
    fun newChannel_channelExists_afterCreate() = runBlocking {
        val session = testSession()
        val (mgr, ctx) = chatContext()
        cmd.execute(session, "testchan", ctx)
        assertTrue(mgr.channelExists("testchan"))
    }
}
