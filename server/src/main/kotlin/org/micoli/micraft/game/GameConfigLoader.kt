package org.micoli.micraft.game

import org.slf4j.LoggerFactory

private val cameConfiglog = LoggerFactory.getLogger("GameConfigLoader")

fun applyGameConfig(config: GameConfig) {
    with(config) {
        DEBUG_WORLD = debugWorld
        TICK_MS = tickMs
        GRAVITY = gravity
        JUMP_SPEED = jumpSpeed
        FLY_VERTICAL_SPEED = flyVerticalSpeed
        SAVE_INTERVAL_TICKS = (saveIntervalSeconds * 1000L / tickMs).toInt()
        TICKS_PER_DAY = ticksPerDay
        TIME_BROADCAST_TICKS = timeBroadcastTicks
        MAX_INTERACTION_DISTANCE = maxInteractionDistance
        RECONCILE_TOLERANCE_XZ = reconcileToleranceXz
        RECONCILE_TOLERANCE_Y = reconcileToleranceY
        SPAWN_X = spawnX
        if (debugWorld) {
            SPAWN_Y = 1f
            SPAWN_Z = 14f
        } else {
            SPAWN_Y = spawnY
            SPAWN_Z = spawnZ
        }
    }
    cameConfiglog.info("Game config applied: {}", config)
}
