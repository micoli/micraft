package org.micoli.micraft.game.perf

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.session.NetworkStats

/** Process-level readings a perf run is measured against. */
interface RunProbe {
    fun nowMs(): Long

    fun heapUsedBytes(): Long

    fun gcCollections(): Long

    fun gcTimeMs(): Long

    fun networkBytesIn(): Long

    fun networkBytesOut(): Long

    fun processCpuLoad(): Double
}

class JvmRunProbe(private val networkStats: NetworkStats) : RunProbe {
    private val memory = ManagementFactory.getMemoryMXBean()
    private val gcs = ManagementFactory.getGarbageCollectorMXBeans()
    private val os = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean

    override fun nowMs() = System.currentTimeMillis()

    override fun heapUsedBytes() = memory.heapMemoryUsage.used

    override fun gcCollections() = gcs.sumOf { it.collectionCount.coerceAtLeast(0) }

    override fun gcTimeMs() = gcs.sumOf { it.collectionTime.coerceAtLeast(0) }

    override fun networkBytesIn() = networkStats.bytesIn.get()

    override fun networkBytesOut() = networkStats.bytesOut.get()

    override fun processCpuLoad() = os.processCpuLoad.coerceAtLeast(0.0)
}

@Serializable
data class RunMetricsSnapshot(
    val durationMs: Long,
    val samples: Int,
    val heapMinBytes: Long,
    val heapMaxBytes: Long,
    val heapAvgBytes: Long,
    val cpuLoadAvg: Double,
    val gcCollections: Long,
    val gcTimeMs: Long,
    val networkBytesIn: Long,
    val networkBytesOut: Long,
)

/**
 * Process-level figures over one perf run: [reset] marks the start, [sample] is called about once
 * per second, [snapshot] reports what happened since the reset.
 */
class RunMetrics(private val probe: RunProbe) {
    private data class Baseline(
        val startMs: Long,
        val gcCollections: Long,
        val gcTimeMs: Long,
        val bytesIn: Long,
        val bytesOut: Long,
    )

    private var baseline = capture()
    private val heapSamples = mutableListOf<Long>()
    private val cpuSamples = mutableListOf<Double>()

    private fun capture() =
        Baseline(
            probe.nowMs(),
            probe.gcCollections(),
            probe.gcTimeMs(),
            probe.networkBytesIn(),
            probe.networkBytesOut(),
        )

    @Synchronized
    fun reset() {
        baseline = capture()
        heapSamples.clear()
        cpuSamples.clear()
    }

    @Synchronized
    fun sample() {
        heapSamples += probe.heapUsedBytes()
        cpuSamples += probe.processCpuLoad()
    }

    @Synchronized
    fun snapshot(): RunMetricsSnapshot =
        RunMetricsSnapshot(
            durationMs = probe.nowMs() - baseline.startMs,
            samples = heapSamples.size,
            heapMinBytes = heapSamples.minOrNull() ?: 0L,
            heapMaxBytes = heapSamples.maxOrNull() ?: 0L,
            heapAvgBytes = if (heapSamples.isEmpty()) 0L else heapSamples.sum() / heapSamples.size,
            cpuLoadAvg = if (cpuSamples.isEmpty()) 0.0 else cpuSamples.average(),
            gcCollections = probe.gcCollections() - baseline.gcCollections,
            gcTimeMs = probe.gcTimeMs() - baseline.gcTimeMs,
            networkBytesIn = probe.networkBytesIn() - baseline.bytesIn,
            networkBytesOut = probe.networkBytesOut() - baseline.bytesOut,
        )
}
