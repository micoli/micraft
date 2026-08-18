package org.micoli.micraft.game.world.rail

import kotlinx.serialization.Serializable

/**
 * Direction a rail connection point faces, in world XZ space — 4 cardinal plus 4 diagonal.
 * [rotatedBy] derives each step from ([dx], [dz]) directly (a 90° clockwise turn of the vector:
 * matches `rotateVerts90CW` in chunkBuilder.ts, e.g. NORTH -> WEST -> SOUTH -> EAST -> NORTH)
 * rather than cycling through [entries] by declaration order, so it stays correct regardless of how
 * the cases are listed.
 */
@Serializable
enum class Direction(val dx: Int, val dz: Int) {
    NORTH(0, -1),
    WEST(-1, 0),
    SOUTH(0, 1),
    EAST(1, 0),
    NORTH_WEST(-1, -1),
    SOUTH_WEST(-1, 1),
    SOUTH_EAST(1, 1),
    NORTH_EAST(1, -1);

    fun rotatedBy(steps: Int): Direction {
        var x = dx
        var z = dz
        repeat(steps.mod(4)) {
            val nx = z
            val nz = -x
            x = nx
            z = nz
        }
        return entries.first { it.dx == x && it.dz == z }
    }

    val opposite: Direction
        get() = rotatedBy(2)
}
