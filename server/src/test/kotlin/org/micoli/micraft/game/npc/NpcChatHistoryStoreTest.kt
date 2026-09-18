package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NpcChatHistoryStoreTest {
    @Test
    fun get_beforeAnyAppend_isEmpty() {
        val store = NpcChatHistoryStore()
        assertTrue(store.get("s1", "npc-1").isEmpty())
    }

    @Test
    fun append_thenGet_returnsUserAndAssistantTurns() {
        val store = NpcChatHistoryStore()
        store.append("s1", "npc-1", "hello", "hi there")
        val turns = store.get("s1", "npc-1")
        assertEquals(2, turns.size)
        assertEquals(ChatTurn("user", "hello"), turns[0])
        assertEquals(ChatTurn("assistant", "hi there"), turns[1])
    }

    @Test
    fun differentSessionsSameNpc_haveIndependentHistory() {
        val store = NpcChatHistoryStore()
        store.append("s1", "npc-1", "hi from s1", "reply1")
        assertTrue(store.get("s2", "npc-1").isEmpty())
    }

    @Test
    fun history_capsAtMaxTurns() {
        val store = NpcChatHistoryStore(maxTurns = 4)
        repeat(5) { i -> store.append("s1", "npc-1", "msg$i", "reply$i") }
        val turns = store.get("s1", "npc-1")
        assertEquals(4, turns.size)
        assertEquals(ChatTurn("user", "msg3"), turns[0])
    }

    @Test
    fun expiredEntry_isPurgedAndReturnsEmpty() {
        val store = NpcChatHistoryStore(ttlMs = 1000)
        store.append("s1", "npc-1", "hello", "hi", now = 0)
        assertTrue(store.get("s1", "npc-1", now = 500).isNotEmpty())
        assertTrue(store.get("s1", "npc-1", now = 2000).isEmpty())
    }

    @Test
    fun canCallNow_trueBeforeAnyCall() {
        val store = NpcChatHistoryStore()
        assertTrue(store.canCallNow("s1", "npc-1"))
    }

    @Test
    fun canCallNow_falseRightAfterMarkCallNow() {
        val store = NpcChatHistoryStore(minCallIntervalMs = 3000)
        store.markCallNow("s1", "npc-1", now = 0)
        assertFalse(store.canCallNow("s1", "npc-1", now = 1000))
        assertTrue(store.canCallNow("s1", "npc-1", now = 4000))
    }
}
