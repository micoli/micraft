package org.micoli.micraft.game.recipe

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaRoot
import org.micoli.micraft.schema.JsonSchemaRootShape

@Serializable
@JsonSchemaRoot(file = "recipes.schema.json", root = JsonSchemaRootShape.MAP_OF)
data class RecipeYamlEntry(
    @JsonSchemaConstraint(enum = ["item", "block", "armor"]) val giveType: String = "item",
    @JsonSchemaConstraint(minLength = 1) val giveId: String = "",
    val giveAmount: Int = 1,
    @JsonSchemaConstraint(minItems = 1, itemPattern = "^[A-Z_]+\\*[0-9]+$|^[A-Z_]+$")
    val items: List<String> = emptyList(),
)
