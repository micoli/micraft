package org.micoli.micraft.game.world.rail

import kotlinx.serialization.Serializable

/**
 * Cardinal direction a rail connection point faces, in world XZ space. Declaration order is the
 * actual rotation cycle applied by a 90° rotation step (matches `rotateVerts90CW` in
 * chunkBuilder.ts: NORTH -> WEST -> SOUTH -> EAST -> NORTH) — [rotatedBy] relies on this order.
 */
@Serializable
enum class Direction(val dx: Int, val dz: Int) {
    NORTH(0, -1),
    WEST(-1, 0),
    SOUTH(0, 1),
    EAST(1, 0);

    fun rotatedBy(steps: Int): Direction = entries[(ordinal + steps).mod(entries.size)]

    val opposite: Direction
        get() = rotatedBy(2)
}
