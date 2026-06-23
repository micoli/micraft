package org.micoli.micraft

internal var TICK_MS             = 50L
internal val TICK_SECONDS        get() = TICK_MS / 1000f
internal var GRAVITY             = -20f
internal var JUMP_SPEED          = 8.5f
internal var FLY_VERTICAL_SPEED  = 8f
internal var SAVE_INTERVAL_TICKS = (30_000L / TICK_MS).toInt()

internal const val TICKS_PER_DAY        = 72_000L
internal const val TIME_BROADCAST_TICKS = 20

internal val   DEBUG_WORLD = System.getenv("MICRAFT_DEBUG_WORLD") == "1"
internal var SPAWN_X = 8f
internal var SPAWN_Y = if (DEBUG_WORLD) 1f  else 200f
internal var SPAWN_Z = if (DEBUG_WORLD) 14f else 8f
