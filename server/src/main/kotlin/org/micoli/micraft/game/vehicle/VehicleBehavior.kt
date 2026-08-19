package org.micoli.micraft.game.vehicle

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.game.world.rail.RailConnection
import org.micoli.micraft.game.world.rail.RailTraversal
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.vehicle.VehicleRegistry

/**
 * Server-authoritative rail-vehicle movement — a sibling to
 * [org.micoli.micraft.game.npc.NpcBehavior], not a subtype: there is no shared Entity abstraction
 * in this codebase, and a vehicle's movement (XZ constrained to a track, no gravity, no lateral
 * collision) has nothing in common with an NPC's free physics.
 *
 * Reversal at a dead end and indefinite traversal on a loop both fall out of the same local
 * connectivity check (does the next block connect back through [RailConnection.active]?) — a loop
 * never presents a dead end by construction (see `RailNetworkRegistry`), so there is no separate
 * "is this a loop" branch here; the registry itself isn't even consulted at tick time.
 *
 * XZ position is anchored to each block's entry/exit *edges* (see [tracePosition]), not its center
 * — a straight/slope piece's edge-to-edge chord traces the exact same line a center-to-center chord
 * would (just re-parametrized, invisible to the renderer), while a 90° cardinal curve instead
 * follows a quarter-circle tangent to both connected edges, matching the model's curved rail
 * instead of cutting a straight diagonal across the block. Y is `railBlockPos.y` (each block's real
 * placed floor — [RailTraversal.connectingNeighbor] already steps this to a neighbor built one grid
 * level up/down through a slope) plus [RailTraversal.localHeight]'s smooth within-block ease along
 * the current block's own declared surface. Pitch ([RailTraversal.localPitch]) tilts the vehicle to
 * match that same surface — 0 on flat pieces, constant across a slope block.
 */
object VehicleBehavior {
    fun tick(instance: VehicleInstance, world: WorldState): Boolean {
        if (!instance.moving) return false
        val speed = VehicleRegistry.get(instance.type)?.speed ?: return false
        instance.progress += speed * TICK_SECONDS
        while (instance.progress >= 1f) {
            instance.progress -= 1f
            advanceOneBlock(instance, world)
        }
        updatePose(instance, world)
        return true
    }

    private fun advanceOneBlock(instance: VehicleInstance, world: WorldState) {
        val currentPos = instance.railBlockPos
        val exitDir = instance.travelDirection
        val nextPos = RailTraversal.connectingNeighbor(world, currentPos, exitDir)
        if (nextPos == null) {
            // See RailConnection.preferredContinuation: a straight/crossing piece reverses back the
            // way it came, a curve turns onto its only other connection — never a direction the
            // piece doesn't actually connect through (which would bounce forever).
            val currentType = world.getBlock(currentPos.x, currentPos.y, currentPos.z)
            val currentState = world.getBlockState(currentPos.x, currentPos.y, currentPos.z)
            val currentExtra = world.getExtraState(currentPos.x, currentPos.y, currentPos.z)
            val currentActive = RailConnection.activeGroups(currentType, currentState, currentExtra)
            instance.travelDirection =
                RailConnection.preferredContinuation(currentActive, exitDir) ?: exitDir.opposite
            return
        }
        val nextType = world.getBlock(nextPos.x, nextPos.y, nextPos.z)
        val nextState = world.getBlockState(nextPos.x, nextPos.y, nextPos.z)
        val nextExtra = world.getExtraState(nextPos.x, nextPos.y, nextPos.z)
        val nextActive = RailConnection.activeGroups(nextType, nextState, nextExtra)
        val arrivalDir = exitDir.opposite
        val forward = RailConnection.preferredContinuation(nextActive, arrivalDir)
        instance.railBlockPos = nextPos
        instance.travelDirection = forward ?: arrivalDir
    }

    private fun updatePose(instance: VehicleInstance, world: WorldState) {
        val current = instance.railBlockPos
        val dir = instance.travelDirection
        val t = instance.progress
        val type = world.getBlock(current.x, current.y, current.z)
        val state = world.getBlockState(current.x, current.y, current.z)
        val extra = world.getExtraState(current.x, current.y, current.z)
        // The other member of whichever connection group `dir` (the exit) belongs to — correct
        // for a straight/slope piece (its only other point is always the opposite direction) and,
        // unlike the old `dir.opposite` guess, also correct for a curve (whose entry point is
        // perpendicular to its exit, not opposite it).
        val entryDir =
            RailConnection.preferredContinuation(
                RailConnection.activeGroups(type, state, extra), dir) ?: dir.opposite
        val height = RailTraversal.localHeight(world, current, entryDir, dir, t)
        val base = RailTraversal.baseHeight(world, current)
        val (x, z) = tracePosition(current.x + 0.5f, current.z + 0.5f, entryDir, dir, t)
        instance.pos = Vec3(x, current.y + base + height, z)
        instance.yaw = traceYaw(entryDir, dir, t)
        instance.pitch = RailTraversal.localPitch(world, current, entryDir, dir)
    }

    /**
     * XZ position at [t] (0 = just entered this block through [entryDir]'s edge, 1 = about to leave
     * through [exitDir]'s edge) tracing this block's own rail geometry, block-centered at ([cx],
     * [cz]).
     *
     * Straight-through pieces (including slopes, whose Y jump is handled separately) get a straight
     * chord between the entry and exit edge midpoints — spatially identical to the old
     * center-to-center chord for a chain of same-axis pieces (same line, just re-anchored half a
     * block earlier/later), which is what lets the curve case below meet its straight neighbors
     * exactly at the shared block face.
     *
     * A 90° cardinal curve (entry and exit on perpendicular axes) instead follows a quarter-circle
     * of radius 0.5 centered on the block corner shared by both connected edges — tangent to the
     * entry edge's straight approach at t=0 and to the exit edge's straight departure at t=1, so it
     * meets its straight neighbors smoothly on both ends instead of cutting a diagonal chord across
     * the block. Diagonal/45°-adjacent connections (not both cardinal, or not exactly
     * perpendicular) fall back to the same straight chord as before — arcs aren't modeled for those
     * yet.
     */
    private fun tracePosition(
        cx: Float,
        cz: Float,
        entryDir: Direction,
        exitDir: Direction,
        t: Float
    ): Pair<Float, Float> {
        val cardinal =
            abs(entryDir.dx) + abs(entryDir.dz) == 1 && abs(exitDir.dx) + abs(exitDir.dz) == 1
        val perpendicular = entryDir.dx * exitDir.dx + entryDir.dz * exitDir.dz == 0
        if (!cardinal || !perpendicular) {
            return (cx + exitDir.dx * (t - 0.5f)) to (cz + exitDir.dz * (t - 0.5f))
        }
        val ox = cx + (entryDir.dx + exitDir.dx) * 0.5f
        val oz = cz + (entryDir.dz + exitDir.dz) * 0.5f
        // Direction.rotatedBy is a 90° CW step; whichever sign of it turns the exit direction back
        // onto the entry direction is this curve's turn sign, giving the right sweep direction for
        // either mirrored variant of the same curve piece.
        val turnCw = exitDir.rotatedBy(1) == entryDir
        val theta = (if (turnCw) -1f else 1f) * (PI.toFloat() / 2f) * t
        val cosT = cos(theta.toDouble()).toFloat()
        val sinT = sin(theta.toDouble()).toFloat()
        // Radius vector at t=0 (pointing from the corner back to the entry edge's midpoint).
        val rx0 = -exitDir.dx * 0.5f
        val rz0 = -exitDir.dz * 0.5f
        val rx = rx0 * cosT - rz0 * sinT
        val rz = rx0 * sinT + rz0 * cosT
        return (ox + rx) to (oz + rz)
    }

    /** Yaw at [t], turning smoothly from [entryDir]'s heading to [exitDir]'s over the block. */
    private fun traceYaw(entryDir: Direction, exitDir: Direction, t: Float): Float {
        val startYaw = headingYaw(entryDir.opposite)
        val endYaw = headingYaw(exitDir)
        var delta = endYaw - startYaw
        val twoPi = (2 * PI).toFloat()
        while (delta > PI) delta -= twoPi
        while (delta < -PI) delta += twoPi
        return startYaw + delta * t
    }

    private fun headingYaw(dir: Direction): Float =
        atan2(dir.dx.toDouble(), dir.dz.toDouble()).toFloat()
}
