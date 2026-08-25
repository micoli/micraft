package org.micoli.micraft.game.placeable

import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.placeable.PlaceableState
import org.micoli.micraft.player.Vec3

private const val ROTATION_STEPS = 12

/**
 * Server-side runtime state of a spawned placeable — mirrors
 * [org.micoli.micraft.game.vehicle.VehicleInstance]'s role, minus rail-bound fields: position is
 * free, orientation is a plain [rotationStep].
 */
class PlaceableInstance(val id: String, val type: EntityType, var pos: Vec3) {
    var rotationStep: Int = 0

    fun rotate() {
        rotationStep = (rotationStep + 1) % ROTATION_STEPS
    }

    /** Absolute set (mod [ROTATION_STEPS]) — used by `/siege_weapon rotation <xx>`. */
    fun rotateTo(value: Int) {
        rotationStep = ((value % ROTATION_STEPS) + ROTATION_STEPS) % ROTATION_STEPS
    }

    fun toState(): PlaceableState =
        PlaceableState(id = id, placeableType = type, pos = pos, rotationStep = rotationStep)
}
