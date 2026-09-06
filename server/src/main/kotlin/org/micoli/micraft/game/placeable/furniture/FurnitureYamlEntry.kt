package org.micoli.micraft.game.placeable.furniture

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaRoot

/**
 * One `resources/furnitures/<name>/<name>.yaml` file — mirrors
 * [org.micoli.micraft.game.placeable.siege.SiegeWeaponYamlEntry]'s per-directory shape, minus the
 * siege combat stats. Flattens the generic placeable render properties
 * ([bbmodelFile]/[width]/[height]) with [rotatable].
 */
@Serializable
@JsonSchemaRoot(file = "furniture.schema.json")
data class FurnitureYamlEntry(
    val bbmodelFile: String = "",
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val width: Float = 0.8f,
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val height: Float = 0.8f,
    val rotatable: Boolean = true,
)

/** `data/resources/furnitures/<name>/<name>.yaml` override — every field optional. */
@Serializable
data class FurnitureYamlOverride(
    val bbmodelFile: String? = null,
    val width: Float? = null,
    val height: Float? = null,
    val rotatable: Boolean? = null,
)
