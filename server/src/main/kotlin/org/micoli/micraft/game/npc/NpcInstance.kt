package org.micoli.micraft.game.npc

import kotlin.random.Random
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.game.npc.animal.AnimalInstanceData
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.ClassResource

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
    /**
     * Target that is another NPC, kept apart from [aggroTarget] on purpose: the player aggro code
     * clears [aggroTarget] as soon as it no longer resolves to a session.
     */
    @Volatile var npcAggroTarget: String? = null,
    @Volatile var packId: String? = null,
    /** Where a pack member converges while the pack is still short of its quorum. */
    @Volatile var packRallyPos: Vec3? = null,
    /** Set when a pack disbands; silences this NPC for `callCooldownSec`. */
    @Volatile var lastPackCallMs: Long = 0L,
    @Volatile var lastDamagedAtMs: Long = 0L,
    val attackCooldownsUntilMs: MutableMap<String, Long> = mutableMapOf(),
    val damageContributors: MutableMap<String, Int> = mutableMapOf(),
    @Volatile var chaseTargetPos: Vec3? = null,
    /**
     * Leash radius in force for the current [chaseTargetPos], or null for the default (pack radius,
     * else `aggroRange`). Set by the animal behaviour when the target is prey, food or a mate:
     * those errands legitimately go further from home than an animal's eyesight.
     */
    @Volatile var chaseLeash: Float? = null,
    /**
     * Multiplier on movement speed, and on outgoing damage, from the animal's condition — starving,
     * pregnant, or both at once.
     *
     * Recomputed once per tick by the animal processor, which is the single place that knows the
     * condition; every reader just multiplies. 1f means "nothing wrong", which is also what a
     * non-animal NPC always reports.
     */
    @Volatile var speedMultiplier: Float = 1f,
    @Volatile var damageMultiplier: Float = 1f,
    @Volatile var instanceLevel: Int = 1,
    @Volatile var xp: Int = 0,
    val activeEffects: MutableList<ActiveStatusEffect> = mutableListOf(),
    @Volatile var pendingDotDamage: Float = 0f,
    @Volatile var isDead: Boolean = false,
    @Volatile var deathTimeMs: Long = 0L,
    /** Owning player's session id when this NPC is a summoned pet; null for every wild NPC. */
    @Volatile var ownerId: String? = null,
    /**
     * Id of the owner's [org.micoli.micraft.player.pet.PetRecord] this instance was summoned from.
     */
    @Volatile var petRecordId: String? = null,
    @Volatile var animalData: AnimalInstanceData? = null,
    /** Asleep for the current hibernation window: no movement, no aggro. */
    @Volatile var hibernating: Boolean = false,
    /** Woken by a hit: stays awake until the current hibernation window is over. */
    @Volatile var hibernationWakeForced: Boolean = false,
    tuning: NpcTuning = NpcConstants.live,
    /**
     * Per-NPC random source. Derived from the world's seed at spawn time so an NPC's evolution does
     * not depend on the order the (concurrent) NPC map happens to be iterated in — that is what
     * makes a seeded simulation reproducible.
     */
    val random: Random = Random,
) {
    val characterClass
        get() = definition.characterClass

    @Volatile var maxHp: Int = definition.computeMaxHp(instanceLevel)
    val maxMana: Int = DerivedStatsCalculator.compute(definition.baseStats, instanceLevel).maxMana
    val maxRage: Int = if (definition.characterClass.classResource == ClassResource.RAGE) 100 else 0

    var wanderPhase: WanderPhase =
        WanderPhase.Moving(spawnPos.x, spawnPos.z, 1f, tuning.wanderStepTicksMax)
    val wanderWaypoints: ArrayDeque<Pair<Float, Float>> = ArrayDeque()

    init {
        if (currentHp < 0) currentHp = maxHp
        if (currentMana < 0) currentMana = maxMana
    }
}
