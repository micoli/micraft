package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.schema.JsonSchemaRoot
import org.micoli.micraft.schema.JsonSchemaRootShape

@Serializable
@JsonSchemaRoot(file = "weapons.schema.json", root = JsonSchemaRootShape.MAP_OF)
data class WeaponCategoryYamlEntry(
    val allowedClasses: Set<CharacterClass> = emptySet(),
    val mainHandOnly: Boolean = false,
)
