package org.micoli.micraft.di

import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.SUPERSEDED_CONNECTION_CLOSE_CODE
import org.micoli.micraft.protocol.ServerMessage

class SessionRegistry {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()

    // Hub-only sessions (a player browsing /hub with no live /game connection), hydrated from
    // persistence. Kept separate from [sessions] so tick/simulation code (playing()) never has to
    // special-case a session with no world/position footprint, while broadcast/lookup code (all())
    // still reaches them (mail, chat, auction updates).
    private val companionsById = ConcurrentHashMap<String, PlayerSession>()

    /** Every connected session, in-game or hub-only companion. For broadcast and player lookup. */
    fun all(): Collection<PlayerSession> = sessions.values + companionsById.values

    /** In-game sessions only — what the per-tick simulation should ever iterate. */
    fun playing(): Collection<PlayerSession> = sessions.values

    suspend fun broadcast(message: ServerMessage) {
        all().forEach { it.send(message) }
    }

    val size: Int
        get() = sessions.size

    operator fun get(id: String): PlayerSession? = sessions[id]

    operator fun set(id: String, session: PlayerSession) {
        sessions[id] = session
    }

    fun remove(id: String): PlayerSession? = sessions.remove(id)

    fun companion(id: String): PlayerSession? = companionsById[id]

    /**
     * Registers a hub-only companion session, closing a previous one for the same id if present.
     */
    suspend fun setCompanion(id: String, session: PlayerSession) {
        companionsById.put(id, session)?.let { previous ->
            if (previous !== session) {
                runCatching {
                    previous.socket.close(
                        CloseReason(
                            SUPERSEDED_CONNECTION_CLOSE_CODE, "replaced by newer connection"))
                }
            }
        }
    }

    fun removeCompanion(id: String): PlayerSession? = companionsById.remove(id)
}
