package org.micoli.micraft.game.npc

import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3

class NpcInstance(
    @Volatile var state: NpcState,
    @Volatile var vy: Float = 0f,
    @Volatile var velocity: Vec3 = Vec3(0f, 0f, 0f),
    val definition: NpcDefinition,
    val spawnPos: Vec3,
    var wanderTargetX: Float = 0f,
    var wanderTargetZ: Float = 0f,
    var wanderPauseTicks: Int = 0,
    var wanderStepTicks: Int = 0,
    @Volatile var currentHp: Int = -1,
    @Volatile var currentMana: Int = -1,
    @Volatile var currentRage: Int = 0,
    @Volatile var aggroTarget: String? = null,
    @Volatile var lastDamagedAtMs: Long = 0L,
    val attackCooldownsUntilMs: MutableMap<String, Long> = mutableMapOf(),
    val damageContributors: MutableMap<String, Int> = mutableMapOf(),
    @Volatile var chaseTargetPos: Vec3? = null,
) {
    init {
        wanderTargetX = spawnPos.x
        wanderTargetZ = spawnPos.z
        if (currentHp < 0) currentHp = definition.hp
        if (currentMana < 0) currentMana = definition.maxMana
    }
}
