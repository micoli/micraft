package org.micoli.micraft.game.npc.animal

import kotlinx.serialization.Serializable
import org.micoli.micraft.npc.NpcStatBlock

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
    val baseStats: NpcStatBlock = NpcStatBlock(),
    val statsVariance: Int = 2,
    val hpRegenPerSec: Float = 2.0f,
    val manaRegenPerSec: Float = 0f,
    val foodSearchRadius: Float = 20.0f,
    val hungerRatePerDay: Double = 0.08,
    val feedHungerReduction: Double = 0.5,
    val combatExitDelaySec: Float = 10.0f,
    val hungerThresholdToHunt: Double = 0.4,
    val hungerThresholdToMate: Double = 0.5,
)
