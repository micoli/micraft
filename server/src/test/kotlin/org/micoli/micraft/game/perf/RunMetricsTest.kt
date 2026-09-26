package org.micoli.micraft.game.perf

import kotlin.test.Test
import kotlin.test.assertEquals

class RunMetricsTest {

    private class FakeProbe : RunProbe {
        var heap = 0L
        var gcCount = 0L
        var gcMs = 0L
        var bytesIn = 0L
        var bytesOut = 0L
        var cpu = 0.0
        var now = 0L

        override fun nowMs() = now

        override fun heapUsedBytes() = heap

        override fun gcCollections() = gcCount

        override fun gcTimeMs() = gcMs

        override fun networkBytesIn() = bytesIn

        override fun networkBytesOut() = bytesOut

        override fun processCpuLoad() = cpu
    }

    @Test
    fun `snapshot reports heap range and average over the samples of the run`() {
        val probe = FakeProbe()
        val metrics = RunMetrics(probe)
        metrics.reset()
        listOf(100L, 300L, 200L).forEach {
            probe.heap = it
            metrics.sample()
        }

        val snap = metrics.snapshot()
        assertEquals(3, snap.samples)
        assertEquals(100L, snap.heapMinBytes)
        assertEquals(300L, snap.heapMaxBytes)
        assertEquals(200L, snap.heapAvgBytes)
    }

    @Test
    fun `snapshot reports gc, network and duration as deltas since reset`() {
        val probe = FakeProbe()
        probe.gcCount = 10
        probe.gcMs = 500
        probe.bytesIn = 1_000
        probe.bytesOut = 5_000
        probe.now = 1_000
        val metrics = RunMetrics(probe)
        metrics.reset()

        probe.gcCount = 13
        probe.gcMs = 540
        probe.bytesIn = 1_200
        probe.bytesOut = 9_000
        probe.now = 61_000

        val snap = metrics.snapshot()
        assertEquals(60_000L, snap.durationMs)
        assertEquals(3L, snap.gcCollections)
        assertEquals(40L, snap.gcTimeMs)
        assertEquals(200L, snap.networkBytesIn)
        assertEquals(4_000L, snap.networkBytesOut)
    }

    @Test
    fun `snapshot averages process cpu load over the samples`() {
        val probe = FakeProbe()
        val metrics = RunMetrics(probe)
        metrics.reset()
        listOf(0.2, 0.4).forEach {
            probe.cpu = it
            metrics.sample()
        }

        assertEquals(0.3, metrics.snapshot().cpuLoadAvg, 0.0001)
    }

    @Test
    fun `reset discards the samples of the previous run`() {
        val probe = FakeProbe()
        val metrics = RunMetrics(probe)
        probe.heap = 900
        metrics.sample()
        metrics.reset()
        probe.heap = 100
        metrics.sample()

        val snap = metrics.snapshot()
        assertEquals(1, snap.samples)
        assertEquals(100L, snap.heapMaxBytes)
    }
}
