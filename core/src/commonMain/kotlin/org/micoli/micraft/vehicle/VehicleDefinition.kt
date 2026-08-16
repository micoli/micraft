package org.micoli.micraft.vehicle

import kotlinx.serialization.Serializable

/**
 * Static, YAML-configured properties of a vehicle type — keyed externally by
 * [org.micoli.micraft.game.world.EntityType] in [VehicleRegistry], mirrors
 * [org.micoli.micraft.game.world.ItemDefinition]/[org.micoli.micraft.game.world.BlockDefinition]'s
 * shape.
 */
@Serializable
data class VehicleDefinition(
    val bbmodelFile: String = "",
    val width: Float = 0.8f,
    val height: Float = 0.8f,
    /**
     * Constant travel speed along the rail, blocks/second — per-vehicle-type, no global constant.
     */
    val speed: Float = 2f,
)
