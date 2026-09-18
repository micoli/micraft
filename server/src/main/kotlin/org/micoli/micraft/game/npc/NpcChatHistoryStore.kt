package org.micoli.micraft.game.npc

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory-only conversation state for `chat_npc` NPCs, keyed by (sessionId, npcId) so two players
 * talking to the same NPC never see each other's history. No persistence: chat context has no
 * durable gameplay value and free-text player input shouldn't survive a restart.
 */
class NpcChatHistoryStore(
    private val maxTurns: Int = 200,
    private val ttlMs: Long = 15 * 60_000,
    private val minCallIntervalMs: Long = 3_000,
) {
    private data class Entry(
        val turns: MutableList<ChatTurn> = mutableListOf(),
        var lastAccessMs: Long = 0
    )

    private val store = ConcurrentHashMap<Pair<String, String>, Entry>()
    private val lastCall = ConcurrentHashMap<Pair<String, String>, Long>()

    private fun key(sessionId: String, npcId: String) = sessionId to npcId

    private fun entryOrNull(sessionId: String, npcId: String, now: Long): Entry? {
        val k = key(sessionId, npcId)
        val entry = store[k] ?: return null
        if (now - entry.lastAccessMs > ttlMs) {
            store.remove(k)
            lastCall.remove(k)
            return null
        }
        return entry
    }

    fun get(
        sessionId: String,
        npcId: String,
        now: Long = System.currentTimeMillis()
    ): List<ChatTurn> = entryOrNull(sessionId, npcId, now)?.turns?.toList() ?: emptyList()

    fun append(
        sessionId: String,
        npcId: String,
        userText: String,
        npcReply: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val entry = store.getOrPut(key(sessionId, npcId)) { Entry() }
        entry.turns += ChatTurn("user", userText)
        entry.turns += ChatTurn("assistant", npcReply)
        while (entry.turns.size > maxTurns) entry.turns.removeAt(0)
        entry.lastAccessMs = now
    }

    /** True if a call is allowed right now for this (session, npc); does not itself record it. */
    fun canCallNow(
        sessionId: String,
        npcId: String,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val last = lastCall[key(sessionId, npcId)] ?: return true
        return now - last >= minCallIntervalMs
    }

    fun markCallNow(sessionId: String, npcId: String, now: Long = System.currentTimeMillis()) {
        lastCall[key(sessionId, npcId)] = now
    }
}
