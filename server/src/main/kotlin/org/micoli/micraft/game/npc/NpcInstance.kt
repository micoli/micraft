package org.micoli.micraft.game.npc

import org.micoli.micraft.npc.NpcState
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
    @Volatile var currentHp: Int = -1,
    @Volatile var aggroTarget: String? = null,
    @Volatile var lastDamagedAtMs: Long = 0L,
    @Volatile var attackCooldownUntilMs: Long = 0L,
    val damageContributors: MutableMap<String, Int> = mutableMapOf(),
) {
    init {
        wanderTargetX = spawnPos.x
        wanderTargetZ = spawnPos.z
        if (currentHp < 0) currentHp = definition.hp
    }
}
