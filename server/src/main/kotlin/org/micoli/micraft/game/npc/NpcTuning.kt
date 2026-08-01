package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

/**
 * Per-instance NPC tunables. The live server holds one in [NpcConstants.live], fed from
 * `data/config/npc.yaml`; the world simulator builds its own copy so it can override rules without
 * touching the running world.
 */
@Serializable
data class NpcTuning(
    val wanderPauseTicksMin: Int = 40,
    val wanderPauseTicksMax: Int = 120,
    val wanderStepTicksMax: Int = 60,
    val yawTurnSpeed: Float = 0.15f,
    val lookAroundSpeed: Float = 0.05f,
    val lookAroundChangeTicks: Int = 30,
    val wanderSpeedMultMin: Float = 0.5f,
    val wanderSpeedMultMax: Float = 1.0f,
    val wanderWaypointCountMin: Int = 1,
    val wanderWaypointCountMax: Int = 3,
    val wanderDecelTicks: Int = 8,
    val interactionRange: Float = 4f,
    // ~6 chunks; clients beyond this don't receive NpcUpdate
    val updateRange: Float = 96f,
    val maxSpawnAttemptsPerTick: Int = 3,
    // Discrete Euler (gravity-first) gives max_rise = sum(V-k)*TICK for k=1..V
    // V=6.5 → 0.9 blocks (insufficient). V=8.0 → 1.4 blocks (clears 1-block step at tick 4).
    val jumpVelocity: Float = 8.0f,
    // zone cell size in blocks — matches voronoiCellSize; used for per-biome limits and orphan
    // despawn
    val npcZoneSize: Int = 256,
    val npcVisibilityCheckIntervalTicks: Int = 20,
    val gameDayDurationSeconds: Double = 1200.0,
)
