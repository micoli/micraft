package org.micoli.micraft.game.npc.animal

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.BaseStats

/**
 * Per-field override of an [AnimalYamlEntry].
 *
 * Every field is nullable so that "unset" and "set to the type's default" stay distinguishable.
 * This matters most for the balancing workflow: an override that only wants to shorten a lifespan
 * must not silently reset the diet, the prey list or the hunger rate. Replacing the whole `animal:`
 * block, as the loader used to, made every partial experiment invalid — an override omitting
 * `lifespanDays` turned the species immortal.
 */
@Serializable
data class AnimalYamlOverride(
    val diet: NpcDiet? = null,
    val lifespanDays: Double? = null,
    val preyTypes: List<String>? = null,
    val canReproduce: Boolean? = null,
    val gestationDays: Double? = null,
    val offspringType: String? = null,
    val offspringMinCount: Int? = null,
    val offspringMaxCount: Int? = null,
    val reproductionCooldownDays: Double? = null,
    val matingRange: Float? = null,
    val scale: Float? = null,
    val adultType: String? = null,
    val growthDays: Double? = null,
    val baseStats: BaseStats? = null,
    val statsVariance: Int? = null,
    val hpRegenPerSec: Float? = null,
    val manaRegenPerSec: Float? = null,
    val foodSearchRadius: Float? = null,
    val roamRadius: Float? = null,
    val fleeRadius: Float? = null,
    val maxLocalDensity: Int? = null,
    val densityRadius: Float? = null,
    val matingContactRange: Float? = null,
    val hungerRatePerDay: Double? = null,
    val feedHungerReduction: Double? = null,
    val combatExitDelaySec: Float? = null,
    val hungerThresholdToHunt: Double? = null,
    val hungerThresholdToMate: Double? = null,
    val starvationThreshold: Double? = null,
    val starvationAttackMultiplier: Float? = null,
    val starvationSpeedMultiplier: Float? = null,
    val starvationSterile: Boolean? = null,
    val starvationDeathDays: Double? = null,
    val gestationAttackMultiplier: Float? = null,
    val gestationSpeedMultiplier: Float? = null,
)

/**
 * [this] with the fields [o] sets replaced.
 *
 * `lifespanDays`, `offspringType` and `adultType` are nullable in the target too, so an override
 * can only ever *set* them — there is no way to clear one back to null. Clearing a lifespan means
 * "becomes immortal", which is never what a balancing override is trying to say.
 */
fun AnimalYamlEntry.applyOverride(o: AnimalYamlOverride) =
    copy(
        diet = o.diet ?: diet,
        lifespanDays = o.lifespanDays ?: lifespanDays,
        preyTypes = o.preyTypes ?: preyTypes,
        canReproduce = o.canReproduce ?: canReproduce,
        gestationDays = o.gestationDays ?: gestationDays,
        offspringType = o.offspringType ?: offspringType,
        offspringMinCount = o.offspringMinCount ?: offspringMinCount,
        offspringMaxCount = o.offspringMaxCount ?: offspringMaxCount,
        reproductionCooldownDays = o.reproductionCooldownDays ?: reproductionCooldownDays,
        matingRange = o.matingRange ?: matingRange,
        scale = o.scale ?: scale,
        adultType = o.adultType ?: adultType,
        growthDays = o.growthDays ?: growthDays,
        baseStats = o.baseStats ?: baseStats,
        statsVariance = o.statsVariance ?: statsVariance,
        hpRegenPerSec = o.hpRegenPerSec ?: hpRegenPerSec,
        manaRegenPerSec = o.manaRegenPerSec ?: manaRegenPerSec,
        foodSearchRadius = o.foodSearchRadius ?: foodSearchRadius,
        roamRadius = o.roamRadius ?: roamRadius,
        fleeRadius = o.fleeRadius ?: fleeRadius,
        maxLocalDensity = o.maxLocalDensity ?: maxLocalDensity,
        densityRadius = o.densityRadius ?: densityRadius,
        matingContactRange = o.matingContactRange ?: matingContactRange,
        hungerRatePerDay = o.hungerRatePerDay ?: hungerRatePerDay,
        feedHungerReduction = o.feedHungerReduction ?: feedHungerReduction,
        combatExitDelaySec = o.combatExitDelaySec ?: combatExitDelaySec,
        hungerThresholdToHunt = o.hungerThresholdToHunt ?: hungerThresholdToHunt,
        hungerThresholdToMate = o.hungerThresholdToMate ?: hungerThresholdToMate,
        starvationThreshold = o.starvationThreshold ?: starvationThreshold,
        starvationAttackMultiplier = o.starvationAttackMultiplier ?: starvationAttackMultiplier,
        starvationSpeedMultiplier = o.starvationSpeedMultiplier ?: starvationSpeedMultiplier,
        starvationSterile = o.starvationSterile ?: starvationSterile,
        starvationDeathDays = o.starvationDeathDays ?: starvationDeathDays,
        gestationAttackMultiplier = o.gestationAttackMultiplier ?: gestationAttackMultiplier,
        gestationSpeedMultiplier = o.gestationSpeedMultiplier ?: gestationSpeedMultiplier,
    )

/**
 * An override applied to a type that has no `animal:` block at all.
 *
 * Falls back to the entry's own defaults, so `polar_bear` can be given a lifespan from the
 * simulator without shipping a full block first.
 */
fun AnimalYamlOverride.toEntry() = AnimalYamlEntry().applyOverride(this)
