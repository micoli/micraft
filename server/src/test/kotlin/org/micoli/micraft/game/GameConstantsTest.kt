package org.micoli.micraft.game

import kotlin.test.Test
import kotlin.test.assertEquals

class GameConstantsTest {

    @Test
    fun `advances deadline by one tick when on schedule`() {
        assertEquals(
            1_050L, nextTickDeadline(now = 1_000L, previousDeadline = 1_000L, tickMs = 50L))
    }

    @Test
    fun `resyncs to now plus tickMs once the naive next deadline has already passed`() {
        // Scheduler jitter or a slow previous tick() left `now` past the naive 1_050 deadline —
        // resync from `now` rather than returning an already-past deadline, which would
        // otherwise make the next wait negative and burst catch-up ticks.
        assertEquals(
            1_120L, nextTickDeadline(now = 1_070L, previousDeadline = 1_000L, tickMs = 50L))
    }

    @Test
    fun `resyncs instead of bursting catch-up ticks after falling far behind`() {
        // A GC pause or a slow tick() left `now` more than a full tick past the next slot —
        // resync to now + tickMs rather than returning an already-past deadline.
        assertEquals(
            1_550L, nextTickDeadline(now = 1_500L, previousDeadline = 1_000L, tickMs = 50L))
    }
}
