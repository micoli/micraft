package org.micoli.micraft.game.minigame

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.support.testI18n
import org.micoli.micraft.support.testSession

private fun registryWith(vararg definitions: MiniGameDefinition): MiniGameRegistry =
    MiniGameRegistry.fixed(definitions.associateBy { it.gameType })

class MiniGameManagerTest {

    private val tictactoe =
        MiniGameDefinition(
            gameType = "tictactoe", displayName = "Tic-Tac-Toe", entryUrl = "/x", maxPlayers = 2)

    private fun setup(
        vararg sessions: org.micoli.micraft.support.FakePlayerSession
    ): MiniGameManager = MiniGameManager({ sessions.toList() }, registryWith(tictactoe), testI18n())

    @Test
    fun `create denies unknown game type`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val mgm = setup(a)
        mgm.create(a, "unknown_game")
        assertTrue(a.sent.any { it is ServerMessage.SocialDenied })
        assertNull(mgm.roomOf(a.id))
    }

    @Test
    fun `create then invite then accept joins the room`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val mgm = setup(a, b)
        mgm.create(a, "tictactoe")
        val room = mgm.roomOf(a.id)!!
        mgm.invite(a, room.id, "B")
        val pending = mgm.pendingRoomIdFor(b.id)
        assertEquals(room.id, pending)
        mgm.respondInvite(b, pending!!, true)
        assertEquals(2, mgm.roomOf(a.id)!!.members.size)
        assertEquals(room.id, mgm.roomOf(b.id)!!.id)
    }

    @Test
    fun `invite is host-only`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val c = testSession(id = "c", name = "C")
        val mgm = setup(a, b, c)
        mgm.create(a, "tictactoe")
        val room = mgm.roomOf(a.id)!!
        mgm.invite(a, room.id, "B")
        mgm.respondInvite(b, mgm.pendingRoomIdFor(b.id)!!, true)
        mgm.invite(b, room.id, "C")
        assertTrue(b.sent.any { it is ServerMessage.SocialDenied })
        assertNull(mgm.pendingRoomIdFor(c.id))
    }

    @Test
    fun `invite respects maxPlayers`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val c = testSession(id = "c", name = "C")
        val mgm = setup(a, b, c)
        mgm.create(a, "tictactoe")
        val room = mgm.roomOf(a.id)!!
        mgm.invite(a, room.id, "B")
        mgm.respondInvite(b, mgm.pendingRoomIdFor(b.id)!!, true)
        mgm.invite(a, room.id, "C")
        assertTrue(a.sent.any { it is ServerMessage.SocialDenied })
        assertNull(mgm.pendingRoomIdFor(c.id))
    }

    @Test
    fun `broadcastAction reaches other members but not the sender`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val mgm = setup(a, b)
        mgm.create(a, "tictactoe")
        val room = mgm.roomOf(a.id)!!
        mgm.invite(a, room.id, "B")
        mgm.respondInvite(b, mgm.pendingRoomIdFor(b.id)!!, true)
        a.sent.clear()
        b.sent.clear()
        mgm.broadcastAction(a, room.id, """{"cell":0}""")
        val received = b.sent.filterIsInstance<ServerMessage.MiniGameAction>()
        assertEquals(1, received.size)
        assertEquals("a", received.first().fromPlayerId)
        assertTrue(a.sent.none { it is ServerMessage.MiniGameAction })
    }

    @Test
    fun `broadcastAction from non-member is ignored`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val outsider = testSession(id = "z", name = "Z")
        val mgm = setup(a, outsider)
        mgm.create(a, "tictactoe")
        val room = mgm.roomOf(a.id)!!
        mgm.broadcastAction(outsider, room.id, "{}")
        assertTrue(a.sent.none { it is ServerMessage.MiniGameAction })
    }

    @Test
    fun `any member leaving mid-game dissolves the room for everyone`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val b = testSession(id = "b", name = "B")
        val mgm = setup(a, b)
        mgm.create(a, "tictactoe")
        val room = mgm.roomOf(a.id)!!
        mgm.invite(a, room.id, "B")
        mgm.respondInvite(b, mgm.pendingRoomIdFor(b.id)!!, true)
        mgm.leave(a, room.id)
        assertNull(mgm.roomOf(a.id))
        assertNull(mgm.roomOf(b.id))
        assertTrue(b.sent.any { it is ServerMessage.MiniGameRoomSync && it.room == null })
    }

    @Test
    fun `last member leaving dissolves the room`() = runBlocking {
        val a = testSession(id = "a", name = "A")
        val mgm = setup(a)
        mgm.create(a, "tictactoe")
        val room = mgm.roomOf(a.id)!!
        mgm.leave(a, room.id)
        assertNull(mgm.roomOf(a.id))
    }

    @Test
    fun `any member disconnecting mid-game dissolves the room for the remaining member too`() =
        runBlocking {
            val a = testSession(id = "a", name = "A")
            val b = testSession(id = "b", name = "B")
            var online = listOf(a, b)
            val mgm = MiniGameManager({ online }, registryWith(tictactoe), testI18n())
            mgm.create(a, "tictactoe")
            val room = mgm.roomOf(a.id)!!
            mgm.invite(a, room.id, "B")
            mgm.respondInvite(b, mgm.pendingRoomIdFor(b.id)!!, true)

            online = listOf(b)
            mgm.onDisconnect(a)
            assertNull(mgm.roomOf(b.id))
            assertTrue(b.sent.any { it is ServerMessage.MiniGameRoomSync && it.room == null })
        }
}
