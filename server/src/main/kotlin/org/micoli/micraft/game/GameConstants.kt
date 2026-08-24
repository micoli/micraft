package org.micoli.micraft.game

internal var TICK_MS = 50L
internal val TICK_SECONDS
    get() = TICK_MS / 1000f
internal var GRAVITY = -20f
internal var JUMP_SPEED = 8.5f
internal var FLY_VERTICAL_SPEED = 8f
internal var SAVE_INTERVAL_TICKS = (30_000L / TICK_MS).toInt()

internal var TICKS_PER_DAY = 72_000L
internal var TIME_BROADCAST_TICKS = 20

internal var DEBUG_WORLD = false
internal var SPAWN_X = 8f
internal var SPAWN_Y = 200f
internal var SPAWN_Z = 8f

internal var MAX_INTERACTION_DISTANCE = 14.0
internal var RECONCILE_TOLERANCE_XZ = 0.5
internal var RECONCILE_TOLERANCE_Y = 0.99

// Computes the next absolute tick deadline anchored to `previousDeadline` instead of
// delay(tickMs) chained after tick() processing time — the latter lets per-tick overhead
// accumulate as permanent drift between simulated time (fixed TICK_SECONDS/tick) and real
// wall-clock time, under-advancing player position vs. the client's wall-clock-based
// prediction and steadily growing the client/server reconciliation gap.
// If `now` has already reached or passed that naive next deadline (a slow tick(), GC pause,
// or scheduler jitter ate into the previous slot), resync to `now + tickMs` instead of
// returning a past deadline, which would otherwise burst catch-up ticks.
internal fun nextTickDeadline(now: Long, previousDeadline: Long, tickMs: Long): Long {
    val next = previousDeadline + tickMs
    return if (next < now) now + tickMs else next
}
