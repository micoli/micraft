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
    fun `record accumulates as an exponential moving average`() {
        val profiler = TickProfiler(alpha = 0.5)
        profiler.record("phase", 10_000_000L)
        profiler.record("phase", 20_000_000L)

        val avgMs = profiler.snapshot().single { it.name == "phase" }.avgMs
        assertEquals(15.0, avgMs, 0.001)
    }
}
