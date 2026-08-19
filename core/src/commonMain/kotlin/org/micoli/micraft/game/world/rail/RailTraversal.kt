package org.micoli.micraft.game.world.rail

import kotlin.math.atan2
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.WorldConstants

/**
 * Shared neighbor-resolution and Y-render logic for rail-network topology and vehicle traversal —
 * used by the server (`RailNetworkRegistry`, `VehicleBehavior`) and the web client's admin
 * rail-test previews (`AdminChunkPreview`, `AdminScenePreview`) alike, via [RailWorldView].
 */
object RailTraversal {
    /**
     * Neighbor of [from] through [dir] whose active connection points back. Same-Y first (the
     * common case); if [from]'s own point for [dir] declares a Y jump ([RailConnectionPoint.gridDy]
     * — leaving a slope), or a candidate one level up/down declares one on its own point facing
     * back (approaching a slope from its flat, dy=1-declared side), that jump is followed too. A
     * flat piece's own gridDy is always 0, so it never "knows" a slope sits one level up — only one
     * side of the pair needs to declare the jump for the link to count, or a flat run adjacent to
     * unrelated stacked rails could never reach the slope above it. Null if [from] has no active
     * connection through [dir], or no candidate connects back.
     */
    fun connectingNeighbor(world: RailWorldView, from: BlockPos, dir: Direction): BlockPos? {
        val fromType = world.getBlock(from.x, from.y, from.z)
        val fromState = world.getBlockState(from.x, from.y, from.z)
        val fromExtra = world.getExtraState(from.x, from.y, from.z)
        val fromPoint =
            RailConnection.activePoints(fromType, fromState, fromExtra).firstOrNull {
                it.direction == dir
            } ?: return null
        for (dy in intArrayOf(fromPoint.gridDy, 0, 1, -1).distinct()) {
            val ny = from.y + dy
            if (ny < WorldConstants.WORLD_MIN_Y || ny > WorldConstants.WORLD_MAX_Y) continue
            val n = BlockPos(from.x + dir.dx, ny, from.z + dir.dz)
            val nType = world.getBlock(n.x, n.y, n.z)
            if (!RailConnection.isRail(nType)) continue
            val nState = world.getBlockState(n.x, n.y, n.z)
            val nExtra = world.getExtraState(n.x, n.y, n.z)
            val nPoint =
                RailConnection.activePoints(nType, nState, nExtra).firstOrNull {
                    it.direction == dir.opposite
                } ?: continue
            if (dy == 0 || fromPoint.gridDy != 0 || nPoint.gridDy != 0) return n
        }
        return null
    }

    /**
     * Voxel height a vehicle rides at above [pos]'s own floor — see [RailConnection.railHeight].
     */
    fun baseHeight(world: RailWorldView, pos: BlockPos): Float =
        RailConnection.railHeight(world.getBlock(pos.x, pos.y, pos.z))

    /**
     * Vehicle Y offset (relative to [pos]'s own floor, and to [baseHeight]'s ride height) while
     * crossing [pos] from [entryDir] to [exitDir], at [t] (0 = just arrived, 1 = about to leave) —
     * interpolates between [pos]'s own declared surface height at the entry edge and at the exit
     * edge, offset back down by the flat default of `1` since that default is already folded into
     * [baseHeight]. A flat piece keeps this at 0 throughout; a slope eases from its low edge to its
     * high edge. Purely local to [pos] — no memory needed across blocks, since [connectingNeighbor]
     * already places the next block at the correct grid Y for continuity. `1` for either edge not
     * declared (shouldn't happen for an active connection, but keeps this total).
     */
    fun localHeight(
        world: RailWorldView,
        pos: BlockPos,
        entryDir: Direction,
        exitDir: Direction,
        t: Float
    ): Float {
        val type = world.getBlock(pos.x, pos.y, pos.z)
        val state = world.getBlockState(pos.x, pos.y, pos.z)
        val extra = world.getExtraState(pos.x, pos.y, pos.z)
        val points = RailConnection.activePoints(type, state, extra)
        val entryDy = points.firstOrNull { it.direction == entryDir }?.dy ?: 1f
        val exitDy = points.firstOrNull { it.direction == exitDir }?.dy ?: 1f
        return entryDy + (exitDy - entryDy) * t - 1f
    }

    /**
     * Pitch (radians, positive = nose up in the direction of travel) a vehicle should tilt while
     * crossing [pos] from [entryDir] to [exitDir] — constant across the block since [localHeight]
     * eases linearly, so this is just that slope's rise (`exitDy - entryDy`) over the unit run
     * between two adjacent edges. `0` for any flat piece (straight, curve, switch — both edges
     * declare `dy = 1`); ±45° for [RailConnectionPoint]'s documented slope edges (`0`/`1`).
     */
    fun localPitch(
        world: RailWorldView,
        pos: BlockPos,
        entryDir: Direction,
        exitDir: Direction
    ): Float {
        val type = world.getBlock(pos.x, pos.y, pos.z)
        val state = world.getBlockState(pos.x, pos.y, pos.z)
        val extra = world.getExtraState(pos.x, pos.y, pos.z)
        val points = RailConnection.activePoints(type, state, extra)
        val entryDy = points.firstOrNull { it.direction == entryDir }?.dy ?: 1f
        val exitDy = points.firstOrNull { it.direction == exitDir }?.dy ?: 1f
        return atan2((exitDy - entryDy).toDouble(), 1.0).toFloat()
    }
}
