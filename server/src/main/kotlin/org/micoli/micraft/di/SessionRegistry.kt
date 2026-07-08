package org.micoli.micraft.di

import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class SessionRegistry {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()

    fun all(): Collection<PlayerSession> = sessions.values

    suspend fun broadcast(message: ServerMessage) {
        sessions.values.forEach { it.send(message) }
    }

    val size: Int
        get() = sessions.size

    operator fun get(id: String): PlayerSession? = sessions[id]

    operator fun set(id: String, session: PlayerSession) {
        sessions[id] = session
    }

    fun remove(id: String): PlayerSession? = sessions.remove(id)
}
