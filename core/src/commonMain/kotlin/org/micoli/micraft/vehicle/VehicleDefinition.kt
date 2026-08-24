package org.micoli.micraft.vehicle

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.Vec3

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
     * Sourced from the optional per-model `resources/vehicles/<bbmodelFile>/<bbmodelFile>.yaml`.
     */
    val speed: Float = 2f,
    /** Rider seat position offset (world space) from the vehicle's own position, while mounted. */
    val seatOffset: Vec3 = Vec3(0f, 0f, 0f),
)
