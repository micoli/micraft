package org.micoli.micraft.placeable

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.player.Vec3

/**
 * Network-facing snapshot of a spawned placeable — mirrors
 * [org.micoli.micraft.vehicle.VehicleState]'s role, without any rail-bound fields: a placeable's
 * position is free (not constrained to a rail block/progress), and its only orientation is
 * [rotationStep].
 */
@Serializable
data class PlaceableState(
    val id: String,
    val placeableType: EntityType,
    val pos: Vec3,
    /** 0..11, 30° increments (rotationStep * 30 = facing degrees). */
    val rotationStep: Int = 0,
)
