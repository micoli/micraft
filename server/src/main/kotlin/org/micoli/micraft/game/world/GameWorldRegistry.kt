package org.micoli.micraft.game.world

import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GameWorldRegistry")

/**
 * Holds the one production [GameWorld] plus, when E2E mode is on, a dynamic map of memory-only
 * worlds keyed by the `?gameSession=` id a browser test supplies. Each dynamic world is created on
 * the first connect for its id and dropped once it has been empty for [IDLE_TIMEOUT_MS].
 */
class GameWorldRegistry(
    val defaultWorld: GameWorld,
    private val e2eEnabled: Boolean,
    private val factory: (id: String) -> GameWorld,
) {
    private val dynamic = ConcurrentHashMap<String, GameWorld>()
    private val emptySinceMs = ConcurrentHashMap<String, Long>()

    /** Default world first, then every live dynamic world — the tick pump iterates this. */
    fun all(): List<GameWorld> = buildList {
        add(defaultWorld)
        addAll(dynamic.values)
    }

    fun get(id: String?): GameWorld? =
        if (id == null || id == DEFAULT_ID) defaultWorld else dynamic[id]

    /**
     * The world a connect with this [gameSessionId] belongs to. Outside E2E mode, or without an id,
     * every client lands in [defaultWorld]. An unknown id spawns a dedicated world (capped at
     * [MAX_DYNAMIC]).
     */
    fun resolve(gameSessionId: String?): GameWorld {
        if (gameSessionId == null || gameSessionId == DEFAULT_ID || !e2eEnabled) return defaultWorld
        dynamic[gameSessionId]?.let {
            return it
        }
        check(dynamic.size < MAX_DYNAMIC) { "too many e2e game worlds (${dynamic.size})" }
        return dynamic.computeIfAbsent(gameSessionId) {
            log.info("spawned e2e game world {}", it)
            factory(it)
        }
    }

    /**
     * Drop dynamic worlds that have had no sessions for [IDLE_TIMEOUT_MS]. Called once per tick.
     */
    fun reapEmpty(nowMs: Long) {
        if (dynamic.isEmpty()) return
        val it = dynamic.entries.iterator()
        while (it.hasNext()) {
            val (id, w) = it.next()
            if (w.sessions.size > 0) {
                emptySinceMs.remove(id)
                continue
            }
            val since = emptySinceMs.getOrPut(id) { nowMs }
            if (nowMs - since > IDLE_TIMEOUT_MS) {
                it.remove()
                emptySinceMs.remove(id)
                log.info("reaped idle e2e game world {}", id)
            }
        }
    }

    companion object {
        const val DEFAULT_ID = "default"
        const val IDLE_TIMEOUT_MS = 30_000L
        const val MAX_DYNAMIC = 16
    }
}
