package org.micoli.micraft.game.npc

object NpcConstants {
    var WANDER_PAUSE_TICKS_MIN = 40
    var WANDER_PAUSE_TICKS_MAX = 120
    var WANDER_STEP_TICKS_MAX = 60
    var YAW_TURN_SPEED = 0.15f
    var LOOK_AROUND_SPEED = 0.05f
    var LOOK_AROUND_CHANGE_TICKS = 30
    var WANDER_SPEED_MULT_MIN = 0.5f
    var WANDER_SPEED_MULT_MAX = 1.0f
    var WANDER_WAYPOINT_COUNT_MIN = 1
    var WANDER_WAYPOINT_COUNT_MAX = 3
    var WANDER_DECEL_TICKS = 8
    var INTERACTION_RANGE = 4f
    // ~6 chunks; clients beyond this don't receive NpcUpdate
    var UPDATE_RANGE = 96f
    var MAX_SPAWN_ATTEMPTS_PER_TICK = 3
    // Discrete Euler (gravity-first) gives max_rise = sum(V-k)*TICK for k=1..V
    // V=6.5 → 0.9 blocks (insufficient). V=8.0 → 1.4 blocks (clears 1-block step at tick 4).
    var JUMP_VELOCITY = 8.0f
    // zone cell size in blocks — matches voronoiCellSize; used for per-biome limits and orphan
    // despawn
    var NPC_ZONE_SIZE = 256
    var NPC_VISIBILITY_CHECK_INTERVAL_TICKS = 20
}
