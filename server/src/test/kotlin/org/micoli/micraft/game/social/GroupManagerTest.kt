package org.micoli.micraft.game.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.FakePlayerSession
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

class GroupManagerTest {

    private fun setup(vararg sessions: FakePlayerSession): GroupManager {
        val cm = ChatChannelManager()
        val chat = ChatService(cm, {}, { sessions.toList() })
        return GroupManager({ sessions.toList() }, chat, cm, testI18n())
    }

    @Test
    fun `group is capped at five members`() = runBlocking {
        val members = (1..6).map { testSession(id = "p$it", name = "P$it") }
        val gm = setup(*members.toTypedArray())
        gm.create(members[0])
        (1..4).forEach { i ->
            gm.invite(members[0], "P${i + 1}")
            gm.respondInvite(members[i], gm.pendingGroupIdFor(members[i].id)!!, true)
        }
        assertEquals(5, gm.groupOf(members[0].id)!!.members.size)
        gm.invite(members[0], "P6")
        assertTrue(members[0].sent.any { it is ServerMessage.SocialDenied })
        assertNull(gm.pendingGroupIdFor(members[5].id))
    }

    @Test
    fun `accepting an invite notifies the joiner`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val gm = setup(a, b)
        gm.create(a)
        gm.invite(a, "B")
        gm.respondInvite(b, gm.pendingGroupIdFor(b.id)!!, true)
        assertTrue(
            b.sent.filterIsInstance<ServerMessage.Notification>().any { it.message.contains("A") })
    }

    @Test
    fun `leader leaving transfers leadership to oldest member`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val gm = setup(a, b)
        gm.create(a)
        gm.invite(a, "B")
        gm.respondInvite(b, gm.pendingGroupIdFor(b.id)!!, true)
        gm.leave(a)
        assertEquals("b", gm.groupOf(b.id)!!.leaderId)
    }

    @Test
    fun `group dissolves when last online member disconnects`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        var online = listOf(a, b)
        val cm = ChatChannelManager()
        val chat = ChatService(cm, {}, { online })
        val gm = GroupManager({ online }, chat, cm, testI18n())
        gm.create(a)
        gm.invite(a, "B")
        gm.respondInvite(b, gm.pendingGroupIdFor(b.id)!!, true)

        online = listOf(b)
        gm.onDisconnect(a)
        assertTrue(gm.groupOf(b.id) != null) // b still online → group survives

        online = emptyList()
        gm.onDisconnect(b)
        assertNull(gm.groupOf(b.id))
    }
}
