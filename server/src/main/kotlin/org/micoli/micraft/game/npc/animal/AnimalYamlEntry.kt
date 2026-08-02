package org.micoli.micraft.game.npc.animal

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.BaseStats

@Serializable
data class AnimalYamlEntry(
    val diet: NpcDiet = NpcDiet.OMNIVORE,
    val lifespanDays: Double? = null,
    val preyTypes: List<String> = emptyList(),
    val canReproduce: Boolean = false,
    val gestationDays: Double = 3.0,
    val offspringType: String? = null,
    val offspringMinCount: Int = 1,
    val offspringMaxCount: Int = 2,
    val reproductionCooldownDays: Double = 5.0,
    val matingRange: Float = 10.0f,
    val scale: Float = 1.0f,
    val adultType: String? = null,
    /**
     * Game days before a baby becomes its [adultType]. Null keeps the historical behaviour, where
     * growing up required gaining a level — which a passive baby with no attacks cannot do, so no
     * baby ever grew up and reproduction never renewed the adult stock.
     */
    val growthDays: Double? = null,
    val baseStats: BaseStats = BaseStats(),
    val statsVariance: Int = 2,
    val hpRegenPerSec: Float = 2.0f,
    val manaRegenPerSec: Float = 0f,
    val foodSearchRadius: Float = 20.0f,
    /**
     * How far an animal may travel from its spawn point while heading for prey, food or a mate.
     *
     * Chasing is otherwise leashed to `aggroRange` around the spawn point — 5 blocks for a goat —
     * which made goal-seeking useless even once the goal existed: the animal could see food it was
     * structurally unable to reach.
     */
    val roamRadius: Float = 40.0f,
    /**
     * Distance at which this animal runs from something that eats it. 0 disables fleeing.
     *
     * Nothing fled before, which was survivable only because predators could not walk to their prey
     * either. Once they can, a prey base with no escape response collapses.
     */
    val fleeRadius: Float = 0.0f,
    /**
     * Most of its own kind allowed within [densityRadius] before it stops breeding. 0 = no limit.
     *
     * Crowding, rather than a world-wide ceiling, is what should stop a species locally: a global
     * cap refuses a birth on the far side of the map because a herd elsewhere is thriving, and it
     * makes every population look regulated when only the ceiling is.
     */
    val maxLocalDensity: Int = 0,
    val densityRadius: Float = 24.0f,
    /**
     * Distance at which mating actually happens, as opposed to [matingRange], which only decides
     * whether a partner is worth walking to.
     */
    val matingContactRange: Float = 2.5f,
    val hungerRatePerDay: Double = 0.08,
    val feedHungerReduction: Double = 0.5,
    val combatExitDelaySec: Float = 10.0f,
    val hungerThresholdToHunt: Double = 0.4,
    val hungerThresholdToMate: Double = 0.5,
    // ── Starvation ────────────────────────────────────────────────────────────
    // Hunger used to have no consequence at all: it clamped at 1.0 and only gated hunting and
    // mating, so nothing ever pushed back on a population. Food is the missing negative feedback.
    /** Hunger at or above which the animal counts as starving. */
    val starvationThreshold: Double = 1.0,
    /** Outgoing damage multiplier while starving. */
    val starvationAttackMultiplier: Float = 0.5f,
    /** Movement speed multiplier while starving. */
    val starvationSpeedMultiplier: Float = 0.2f,
    /** A starving animal cannot conceive. It can still hunt — that is its way out. */
    val starvationSterile: Boolean = true,
    /** Game days of continuous starvation before it dies. 0 disables starvation deaths. */
    val starvationDeathDays: Double = 0.0,
    // ── Gestation ─────────────────────────────────────────────────────────────
    /** Outgoing damage multiplier while pregnant. */
    val gestationAttackMultiplier: Float = 1.0f,
    /** Movement speed multiplier while pregnant. */
    val gestationSpeedMultiplier: Float = 1.0f,
)
