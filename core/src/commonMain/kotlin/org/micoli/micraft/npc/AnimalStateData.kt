package org.micoli.micraft.npc

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.BaseStats

@Serializable
data class AnimalStateData(
    val gender: NpcGender,
    val ageGameDays: Double = 0.0,
    val hunger: Double = 0.3,
    val gestationRemainingDays: Double? = null,
    val lastReproductionDay: Double? = null,
    val parentIds: Set<String> = emptySet(),
    val stats: BaseStats = BaseStats(),
    val motherLevel: Int = 0,
)
