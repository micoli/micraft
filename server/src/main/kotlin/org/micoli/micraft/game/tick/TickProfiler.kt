package org.micoli.micraft.game.tick

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

@Serializable data class TickPhaseStat(val name: String, val avgMs: Double)

/** Exponential moving average of per-phase tick duration, for the admin CPU breakdown. */
class TickProfiler(private val alpha: Double = 0.1) {
    private val emaNanos = ConcurrentHashMap<String, Double>()

    inline fun <T> measure(name: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            record(name, System.nanoTime() - start)
        }
    }

    fun record(name: String, elapsedNanos: Long) {
        emaNanos.merge(name, elapsedNanos.toDouble()) { old, new ->
            old * (1 - alpha) + new * alpha
        }
    }

    fun snapshot(): List<TickPhaseStat> =
        emaNanos.entries
            .sortedByDescending { it.value }
            .map { (name, nanos) -> TickPhaseStat(name, nanos / 1_000_000.0) }
}
