package org.micoli.micraft.game.placeable.siege

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaRoot

/**
 * One `resources/siege/projectiles/<name>/<name>.yaml` file — mirrors
 * [org.micoli.micraft.game.world.block.BlockYamlEntry]'s per-directory shape.
 */
@Serializable
@JsonSchemaRoot(file = "siege_projectiles.schema.json")
data class SiegeProjectileYamlEntry(
    val bbmodelFile: String = "",
    @JsonSchemaConstraint(exclusiveMinimum = 0.0) val radius: Float = 0.3f,
)

/** `data/resources/siege/projectiles/<name>/<name>.yaml` override — every field optional. */
@Serializable
data class SiegeProjectileYamlOverride(
    val bbmodelFile: String? = null,
    val radius: Float? = null,
)
