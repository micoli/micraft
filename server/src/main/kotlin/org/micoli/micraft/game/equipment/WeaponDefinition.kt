package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.rpg.StatBonus
import org.micoli.micraft.game.world.EquipmentCategory

@Serializable
data class WeaponDefinition(
    val category: EquipmentCategory,
    val statBonus: StatBonus = StatBonus(),
    val rotate: Rotation = Rotation(),
)
