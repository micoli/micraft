package org.micoli.micraft.game.armor

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.rpg.StatBonus

@Serializable
data class ArmorDefinition(
    val wearable: WearableSlots = WearableSlots(),
    val statBonus: StatBonus = StatBonus(),
)
