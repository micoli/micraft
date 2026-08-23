package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.rpg.StatBonus
import org.micoli.micraft.game.world.EquipmentCategory

@Serializable
data class ToolDefinition(
    val category: EquipmentCategory,
    val breakSpeedMultiplier: Float = 1f,
    val statBonus: StatBonus = StatBonus(),
    val rotate: Rotation = Rotation(),
)
