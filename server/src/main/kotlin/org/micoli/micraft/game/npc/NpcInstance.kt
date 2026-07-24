package org.micoli.micraft.game.npc

import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.game.npc.animal.AnimalInstanceData
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3

class NpcInstance(
    @Volatile var state: NpcState,
    @Volatile var vy: Float = 0f,
    @Volatile var velocity: Vec3 = Vec3(0f, 0f, 0f),
    val definition: NpcDefinition,
    val spawnPos: Vec3,
    @Volatile var currentHp: Int = -1,
    @Volatile var currentMana: Int = -1,
    @Volatile var currentRage: Int = 0,
    @Volatile var aggroTarget: String? = null,
    @Volatile var lastDamagedAtMs: Long = 0L,
    val attackCooldownsUntilMs: MutableMap<String, Long> = mutableMapOf(),
    val damageContributors: MutableMap<String, Int> = mutableMapOf(),
    @Volatile var chaseTargetPos: Vec3? = null,
    @Volatile var instanceLevel: Int = 1,
    val activeEffects: MutableList<ActiveStatusEffect> = mutableListOf(),
    @Volatile var pendingDotDamage: Float = 0f,
    @Volatile var isDead: Boolean = false,
    @Volatile var deathTimeMs: Long = 0L,
    @Volatile var animalData: AnimalInstanceData? = null,
) {
    var wanderPhase: WanderPhase =
        WanderPhase.Moving(spawnPos.x, spawnPos.z, 1f, NpcConstants.WANDER_STEP_TICKS_MAX)
    val wanderWaypoints: ArrayDeque<Pair<Float, Float>> = ArrayDeque()

    init {
        if (currentHp < 0) currentHp = NpcHpCalculator.computeMaxHp(definition, instanceLevel)
        if (currentMana < 0) currentMana = definition.maxMana
    }
}
