package org.micoli.micraft.game.npc.animal

import kotlin.random.Random
import org.micoli.micraft.npc.AnimalStateData
import org.micoli.micraft.npc.NpcGender
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats

class AnimalInstanceData(
    @Volatile var gender: NpcGender,
    @Volatile var ageGameDays: Double,
    @Volatile var hunger: Double,
    @Volatile var gestationRemainingDays: Double?,
    @Volatile var lastReproductionDay: Double?,
    val parentIds: MutableSet<String>,
    val stats: BaseStats,
    val motherLevel: Int,
    @Volatile var preyTargetId: String? = null,
    @Volatile var preyTargetPos: Vec3? = null,
    @Volatile var mateTargetId: String? = null,
    @Volatile var mateTargetPos: Vec3? = null,
    /**
     * Grazing block this animal is heading for.
     *
     * Feeding used to consume only what was already within 2.5 blocks, so a herbivore starved
     * beside a meadow it never walked to. Nothing was pulling it there because no goal position
     * existed.
     */
    @Volatile var foodTargetPos: Vec3? = null,
    /** Where to run to, away from the nearest predator. Overrides every other errand. */
    @Volatile var fleeTargetPos: Vec3? = null,
    /**
     * Game days spent continuously starving; 0 when the animal is not starving.
     *
     * Accumulated like [ageGameDays] rather than derived from a start date, so it measures time the
     * animal actually lived through and does not depend on when the world clock is advanced
     * relative to this pass. Hunger itself stays clamped at 1.0 — bounded and serializable — and
     * this is the timer that turns "has been starving a while" into a death. Reset to 0 as soon as
     * it eats: recovery is complete, not partial.
     */
    @Volatile var starvingDays: Double = 0.0,
    @Volatile var hpRegenAccumulator: Float = 0f,
) {
    fun toState(): AnimalStateData =
        AnimalStateData(
            gender = gender,
            ageGameDays = ageGameDays,
            hunger = hunger,
            gestationRemainingDays = gestationRemainingDays,
            lastReproductionDay = lastReproductionDay,
            parentIds = parentIds.toSet(),
            stats = stats,
            motherLevel = motherLevel,
        )

    companion object {
        fun fromState(state: AnimalStateData) =
            AnimalInstanceData(
                gender = state.gender,
                ageGameDays = state.ageGameDays,
                hunger = state.hunger,
                gestationRemainingDays = state.gestationRemainingDays,
                lastReproductionDay = state.lastReproductionDay,
                parentIds = state.parentIds.toMutableSet(),
                stats = state.stats,
                motherLevel = state.motherLevel,
            )

        /**
         * A freshly spawned animal.
         *
         * [random] defaults to the global source only for callers that genuinely do not have one.
         * Pass the NPC's own source wherever it exists: gender, starting age, hunger and stat
         * spread all come from here, so drawing them globally makes a seeded run irreproducible —
         * the simulator advertised a seed while its animals were rolled off `Random.Default`.
         */
        fun initial(
            lifespanDays: Double?,
            baseStats: BaseStats,
            statsVariance: Int,
            parentIds: Set<String> = emptySet(),
            motherLevel: Int = 0,
            initialHunger: Double? = null,
            initialAge: Double? = null,
            random: Random = Random,
        ): AnimalInstanceData {
            val age =
                initialAge
                    ?: if (lifespanDays != null) random.nextDouble(0.0, lifespanDays * 0.5) else 0.0
            val stats =
                BaseStats(
                    str =
                        (baseStats.str + random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                    dex =
                        (baseStats.dex + random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                    intel =
                        (baseStats.intel + random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                    wis =
                        (baseStats.wis + random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                    con =
                        (baseStats.con + random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                    cha =
                        (baseStats.cha + random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                )
            return AnimalInstanceData(
                gender = if (random.nextBoolean()) NpcGender.MALE else NpcGender.FEMALE,
                ageGameDays = age,
                hunger = initialHunger ?: random.nextDouble(0.1, 0.5),
                gestationRemainingDays = null,
                lastReproductionDay = null,
                parentIds = parentIds.toMutableSet(),
                stats = stats,
                motherLevel = motherLevel,
            )
        }

        fun offspring(
            parentA: AnimalInstanceData,
            parentB: AnimalInstanceData,
            statsVariance: Int,
            parentAId: String,
            parentBId: String,
            motherLevel: Int,
            random: Random = Random,
        ): AnimalInstanceData {
            val avgStr = (parentA.stats.str + parentB.stats.str) / 2
            val avgDex = (parentA.stats.dex + parentB.stats.dex) / 2
            val avgIntel = (parentA.stats.intel + parentB.stats.intel) / 2
            val avgWis = (parentA.stats.wis + parentB.stats.wis) / 2
            val avgCon = (parentA.stats.con + parentB.stats.con) / 2
            val avgCha = (parentA.stats.cha + parentB.stats.cha) / 2
            val stats =
                BaseStats(
                    str =
                        (avgStr + random.nextInt(-statsVariance, statsVariance + 1)).coerceAtLeast(
                            1),
                    dex =
                        (avgDex + random.nextInt(-statsVariance, statsVariance + 1)).coerceAtLeast(
                            1),
                    intel =
                        (avgIntel + random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                    wis =
                        (avgWis + random.nextInt(-statsVariance, statsVariance + 1)).coerceAtLeast(
                            1),
                    con =
                        (avgCon + random.nextInt(-statsVariance, statsVariance + 1)).coerceAtLeast(
                            1),
                    cha =
                        (avgCha + random.nextInt(-statsVariance, statsVariance + 1)).coerceAtLeast(
                            1),
                )
            return AnimalInstanceData(
                gender = if (random.nextBoolean()) NpcGender.MALE else NpcGender.FEMALE,
                ageGameDays = 0.0,
                hunger = 0.2,
                gestationRemainingDays = null,
                lastReproductionDay = null,
                parentIds = mutableSetOf(parentAId, parentBId),
                stats = stats,
                motherLevel = motherLevel,
            )
        }
    }
}
