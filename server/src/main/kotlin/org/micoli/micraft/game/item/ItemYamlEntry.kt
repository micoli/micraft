package org.micoli.micraft.game.item

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaRoot
import org.micoli.micraft.schema.JsonSchemaRootShape

@Serializable
@JsonSchemaRoot(file = "items.schema.json", root = JsonSchemaRootShape.MAP_OF)
data class ItemYamlEntry(
    val buildable: Boolean = false,
    val placesBlock: String? = null,
    @JsonSchemaConstraint(minLength = 1, maxLength = 4) val label: String = "",
    @JsonSchemaConstraint(pattern = "^#[0-9A-Fa-f]{6}$") val bg: String = "",
    val healthRestore: Int = 0,
    val manaRestore: Int = 0,
)
