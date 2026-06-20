package org.micoli.micraft

internal const val TICK_MS             = 50L
internal const val TICK_SECONDS        = TICK_MS / 1000f
internal const val GRAVITY             = -20f
internal const val JUMP_SPEED          = 8.5f
internal const val FLY_VERTICAL_SPEED  = 8f
internal const val SAVE_INTERVAL_TICKS = (30_000L / TICK_MS).toInt()

internal val   DEBUG_WORLD = System.getenv("MICRAFT_DEBUG_WORLD") == "1"
internal const val SPAWN_X = 8f
internal val   SPAWN_Y: Float = if (DEBUG_WORLD) 1f  else 200f
internal val   SPAWN_Z: Float = if (DEBUG_WORLD) 14f else 8f
