package org.micoli.micraft.game.tick

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TickProfilerTest {

    @Test
    fun `measure records elapsed time under the given phase name`() {
        val profiler = TickProfiler()
        profiler.measure("phaseA") { Thread.sleep(1) }

        val snapshot = profiler.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals("phaseA", snapshot[0].name)
        assertTrue(snapshot[0].avgMs > 0.0)
    }

    @Test
    fun `snapshot is sorted by descending average duration`() {
        val profiler = TickProfiler(alpha = 1.0)
        profiler.record("fast", 1_000_000L)
        profiler.record("slow", 10_000_000L)

        val snapshot = profiler.snapshot()
        assertEquals(listOf("slow", "fast"), snapshot.map { it.name })
    }

    @Test
    fun `run snapshot reports nearest-rank percentiles per phase`() {
        val profiler = TickProfiler()
        (1..100).shuffled().forEach { profiler.record("total", it * 1_000_000L) }

        val total = profiler.runSnapshot().single { it.name == "total" }
        assertEquals(100, total.count)
        assertEquals(50.0, total.p50Ms, 0.001)
        assertEquals(95.0, total.p95Ms, 0.001)
        assertEquals(99.0, total.p99Ms, 0.001)
        assertEquals(100.0, total.maxMs, 0.001)
    }

    @Test
    fun `resetRun starts a new window without touching the moving average`() {
        val profiler = TickProfiler(alpha = 1.0)
        profiler.record("total", 40_000_000L)
        profiler.resetRun()
        profiler.record("total", 10_000_000L)

        val run = profiler.runSnapshot().single { it.name == "total" }
        assertEquals(1, run.count)
        assertEquals(10.0, run.maxMs, 0.001)
        assertEquals(10.0, profiler.snapshot().single { it.name == "total" }.avgMs, 0.001)
    }

    @Test
    fun `run window keeps only the most recent samples once full`() {
        val profiler = TickProfiler(runCapacity = 3)
        listOf(100L, 1L, 2L, 3L).forEach { profiler.record("total", it * 1_000_000L) }

        val run = profiler.runSnapshot().single { it.name == "total" }
        assertEquals(3, run.count)
        assertEquals(3.0, run.maxMs, 0.001)
    }

    @Test
    fun `record accumulates as an exponential moving average`() {
        val profiler = TickProfiler(alpha = 0.5)
        profiler.record("phase", 10_000_000L)
        profiler.record("phase", 20_000_000L)

        val avgMs = profiler.snapshot().single { it.name == "phase" }.avgMs
        assertEquals(15.0, avgMs, 0.001)
    }
}
