package org.micoli.micraft.game.tick

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

@Serializable data class TickPhaseStat(val name: String, val avgMs: Double)

@Serializable
data class TickPhaseRunStat(
    val name: String,
    val count: Int,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
)

/** 1 h of ticks at 20 tps. */
private const val DEFAULT_RUN_CAPACITY = 72_000

/**
 * Per-phase tick durations: an exponential moving average for the admin CPU breakdown, plus a
 * bounded window of raw samples since [resetRun] for exact run percentiles.
 */
class TickProfiler(
    private val alpha: Double = 0.1,
    private val runCapacity: Int = DEFAULT_RUN_CAPACITY,
) {
    private val emaNanos = ConcurrentHashMap<String, Double>()
    private val runSamples = ConcurrentHashMap<String, SampleWindow>()

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
        runSamples.computeIfAbsent(name) { SampleWindow(runCapacity) }.add(elapsedNanos)
    }

    fun snapshot(): List<TickPhaseStat> =
        emaNanos.entries
            .sortedByDescending { it.value }
            .map { (name, nanos) -> TickPhaseStat(name, nanos / 1_000_000.0) }

    fun resetRun() = runSamples.clear()

    fun runSnapshot(): List<TickPhaseRunStat> =
        runSamples.entries
            .map { (name, window) -> window.stat(name) }
            .sortedByDescending { it.p95Ms }
}

private class SampleWindow(private val capacity: Int) {
    private val samples = LongArray(capacity)
    private var size = 0
    private var next = 0

    @Synchronized
    fun add(nanos: Long) {
        samples[next] = nanos
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    @Synchronized
    fun stat(name: String): TickPhaseRunStat {
        val sorted = samples.copyOf(size).apply { sort() }
        fun rank(p: Double): Double = sorted[nearestRankIndex(p, size)] / 1_000_000.0
        return TickPhaseRunStat(name, size, rank(0.50), rank(0.95), rank(0.99), rank(1.0))
    }
}

private fun nearestRankIndex(p: Double, size: Int): Int =
    (kotlin.math.ceil(p * size).toInt() - 1).coerceIn(0, size - 1)
