package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaRoot
import org.micoli.micraft.schema.JsonSchemaRootShape

@Serializable
@JsonSchemaRoot(file = "tools.schema.json", root = JsonSchemaRootShape.MAP_OF)
data class ToolCategoryYamlEntry(val mainHandOnly: Boolean = false)
