package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.rpg.StatBonus
import org.micoli.micraft.game.world.EquipmentCategory

@Serializable
data class WeaponYamlEntry(
    val category: EquipmentCategory,
    val statBonus: StatBonus = StatBonus(),
    val rotate: Rotation = Rotation(),
)

/**
 * Whole-block override, not per-leaf-field: a user overriding `category`/`statBonus`/`rotate` must
 * supply the full nested block, same convention as
 * [org.micoli.micraft.game.armor.ArmorYamlOverride].
 */
@Serializable
data class WeaponYamlOverride(
    val category: EquipmentCategory? = null,
    val statBonus: StatBonus? = null,
    val rotate: Rotation? = null,
)
