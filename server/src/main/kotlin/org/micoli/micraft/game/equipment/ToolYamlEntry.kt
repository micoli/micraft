package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.rpg.StatBonus
import org.micoli.micraft.game.world.EquipmentCategory

@Serializable
data class ToolYamlEntry(
    val category: EquipmentCategory,
    val breakSpeedMultiplier: Float = 1f,
    val statBonus: StatBonus = StatBonus(),
    val rotate: Rotation = Rotation(),
)

/**
 * Whole-block override, not per-leaf-field: a user overriding
 * `category`/`breakSpeedMultiplier`/`statBonus`/`rotate` must supply the full nested block, same
 * convention as [org.micoli.micraft.game.armor.ArmorYamlOverride].
 */
@Serializable
data class ToolYamlOverride(
    val category: EquipmentCategory? = null,
    val breakSpeedMultiplier: Float? = null,
    val statBonus: StatBonus? = null,
    val rotate: Rotation? = null,
)
