package org.micoli.micraft.game.npc

object NpcConstants {
    var WANDER_PAUSE_TICKS_MIN = 40
    var WANDER_PAUSE_TICKS_MAX = 120
    var WANDER_STEP_TICKS_MAX = 60
    var INTERACTION_RANGE = 4f
    // ~6 chunks; clients beyond this don't receive NpcUpdate
    var UPDATE_RANGE = 96f
    var MAX_SPAWN_ATTEMPTS_PER_TICK = 3
    // v = sqrt(2 * |GRAVITY| * h), h ≈ 1.05 blocks for reliable 1-block clearance
    var JUMP_VELOCITY = 6.5f
    // zone cell size in blocks — matches voronoiCellSize; used for per-biome limits and orphan
    // despawn
    var NPC_ZONE_SIZE = 256
    var NPC_VISIBILITY_CHECK_INTERVAL_TICKS = 20
}
