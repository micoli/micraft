package org.micoli.micraft.npc

object NpcConstants {
    const val WIDTH_DEFAULT = 0.6f
    const val HEIGHT_DEFAULT = 1.8f
    const val WANDER_SPEED_DEFAULT = 1.5f
    const val WANDER_RADIUS_DEFAULT = 8f
    const val WANDER_PAUSE_TICKS_MIN = 40
    const val WANDER_PAUSE_TICKS_MAX = 120
    const val WANDER_STEP_TICKS_MAX = 60
    const val INTERACTION_RANGE = 4f
    const val UPDATE_RANGE = 96f  // ~6 chunks; clients beyond this don't receive NpcUpdate
    const val SPAWN_CHECK_INTERVAL_TICKS = 200
    const val MAX_SPAWN_ATTEMPTS_PER_TICK = 3
}
