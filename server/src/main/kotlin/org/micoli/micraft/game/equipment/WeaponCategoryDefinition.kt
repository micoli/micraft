package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.CharacterClass

@Serializable
data class WeaponCategoryDefinition(
    val allowedClasses: Set<CharacterClass> = emptySet(),
    val mainHandOnly: Boolean = false,
)
