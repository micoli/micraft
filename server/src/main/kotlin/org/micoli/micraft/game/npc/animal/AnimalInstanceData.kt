package org.micoli.micraft.game.npc.animal

import kotlin.random.Random
import org.micoli.micraft.npc.AnimalStateData
import org.micoli.micraft.npc.NpcGender
import org.micoli.micraft.npc.NpcStatBlock
import org.micoli.micraft.player.Vec3

class AnimalInstanceData(
    @Volatile var gender: NpcGender,
    @Volatile var ageGameDays: Double,
    @Volatile var hunger: Double,
    @Volatile var gestationRemainingDays: Double?,
    @Volatile var lastReproductionDay: Double?,
    val parentIds: MutableSet<String>,
    val stats: NpcStatBlock,
    val motherLevel: Int,
    @Volatile var preyTargetId: String? = null,
    @Volatile var preyTargetPos: Vec3? = null,
    @Volatile var mateTargetId: String? = null,
    @Volatile var mateTargetPos: Vec3? = null,
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

        fun initial(
            lifespanDays: Double?,
            baseStats: NpcStatBlock,
            statsVariance: Int,
            parentIds: Set<String> = emptySet(),
            motherLevel: Int = 0,
            initialHunger: Double? = null,
            initialAge: Double? = null,
        ): AnimalInstanceData {
            val age =
                initialAge
                    ?: if (lifespanDays != null) Random.nextDouble(0.0, lifespanDays * 0.5) else 0.0
            val stats =
                NpcStatBlock(
                    strength =
                        (baseStats.strength + Random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                    dexterity =
                        (baseStats.dexterity + Random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                    constitution =
                        (baseStats.constitution + Random.nextInt(-statsVariance, statsVariance + 1))
                            .coerceAtLeast(1),
                )
            return AnimalInstanceData(
                gender = if (Random.nextBoolean()) NpcGender.MALE else NpcGender.FEMALE,
                ageGameDays = age,
                hunger = initialHunger ?: Random.nextDouble(0.1, 0.5),
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
        ): AnimalInstanceData {
            val avgStr = (parentA.stats.strength + parentB.stats.strength) / 2
            val avgDex = (parentA.stats.dexterity + parentB.stats.dexterity) / 2
            val avgCon = (parentA.stats.constitution + parentB.stats.constitution) / 2
            val stats =
                NpcStatBlock(
                    strength =
                        (avgStr + Random.nextInt(-statsVariance, statsVariance + 1)).coerceAtLeast(
                            1),
                    dexterity =
                        (avgDex + Random.nextInt(-statsVariance, statsVariance + 1)).coerceAtLeast(
                            1),
                    constitution =
                        (avgCon + Random.nextInt(-statsVariance, statsVariance + 1)).coerceAtLeast(
                            1),
                )
            return AnimalInstanceData(
                gender = if (Random.nextBoolean()) NpcGender.MALE else NpcGender.FEMALE,
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
