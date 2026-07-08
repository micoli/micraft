package org.micoli.micraft.game.armor

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.rpg.StatBonus

@Serializable
data class ArmorYamlEntry(
    val wearable: WearableSlots = WearableSlots(),
    val statBonus: StatBonus = StatBonus(),
)
