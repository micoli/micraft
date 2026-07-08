package org.micoli.micraft.game.armor

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.rpg.StatBonus

/**
 * Whole-block override, not per-leaf-field: a user overriding `wearable`/`statBonus` must supply
 * the full nested block, same convention as [ServerConfigLoader]'s `AuthSection.oauth`.
 */
@Serializable
data class ArmorYamlOverride(
    val wearable: WearableSlots? = null,
    val statBonus: StatBonus? = null,
)
