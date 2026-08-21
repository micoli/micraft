package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.EquipmentCategory

@Serializable
data class ToolDefinition(
    val category: EquipmentCategory,
    val breakSpeedMultiplier: Float = 1f,
    val rotate: Rotation = Rotation(),
)
