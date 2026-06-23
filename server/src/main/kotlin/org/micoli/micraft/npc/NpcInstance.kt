package org.micoli.micraft.npc

import org.micoli.micraft.player.Vec3

class NpcInstance(
    @Volatile var state: NpcState,
    @Volatile var vy: Float = 0f,
    val definition: NpcDefinition,
    val spawnPos: Vec3,
    var wanderTargetX: Float = 0f,
    var wanderTargetZ: Float = 0f,
    var wanderPauseTicks: Int = 0,
    var wanderStepTicks: Int = 0,
) {
    init {
        wanderTargetX = spawnPos.x
        wanderTargetZ = spawnPos.z
    }
}
