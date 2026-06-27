package org.micoli.micraft

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

internal var MAX_INTERACTION_DISTANCE = 7.0
internal var RECONCILE_TOLERANCE_XZ = 0.5
internal var RECONCILE_TOLERANCE_Y = 0.99
