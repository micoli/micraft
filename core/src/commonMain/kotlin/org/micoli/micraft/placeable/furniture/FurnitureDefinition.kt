package org.micoli.micraft.placeable.furniture

import kotlinx.serialization.Serializable

/**
 * Static, YAML-configured properties of a furniture type — keyed externally by
 * [org.micoli.micraft.game.world.EntityType] in [FurnitureRegistry]. Furniture is a decorative,
 * non-functional placeable: it only spawns, rotates (30° steps) and despawns. Carries the generic
 * placeable render properties ([bbmodelFile]/[width]/[height]) plus [rotatable].
 */
@Serializable
data class FurnitureDefinition(
    val bbmodelFile: String = "",
    val width: Float = 0.8f,
    val height: Float = 0.8f,
    val rotatable: Boolean = true,
)
