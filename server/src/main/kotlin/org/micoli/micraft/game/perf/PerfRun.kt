package org.micoli.micraft.game.perf

import java.util.Timer
import kotlin.concurrent.fixedRateTimer
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.tick.TickPhaseRunStat
import org.micoli.micraft.game.world.GameWorld

@Serializable
data class PerfSnapshot(
    val tick: List<TickPhaseRunStat>,
    val process: RunMetricsSnapshot,
    val characters: Int,
    val loadedChunks: Int,
    val npcs: Int,
)

/**
 * One measured perf window: a World's tick percentiles plus process-level [RunMetrics], bracketed
 * by [reset] and [snapshot].
 */
class PerfRun(private val metrics: RunMetrics) {
    private var sampler: Timer? = null

    fun reset(world: GameWorld) {
        world.tickProfiler.resetRun()
        metrics.reset()
    }

    fun snapshot(world: GameWorld): PerfSnapshot =
        PerfSnapshot(
            tick = world.tickProfiler.runSnapshot(),
            process = metrics.snapshot(),
            characters = world.getPlayerStates().size,
            loadedChunks = world.world.loadedChunkCount(),
            npcs = world.getNpcInstances().size,
        )

    fun startSampling(periodMs: Long = 1_000L) {
        if (sampler != null) return
        sampler =
            fixedRateTimer("perf-run-sampler", daemon = true, period = periodMs) {
                metrics.sample()
            }
    }
}
