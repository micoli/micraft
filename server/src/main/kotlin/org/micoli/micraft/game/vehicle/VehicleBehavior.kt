package org.micoli.micraft.game.vehicle

import kotlin.math.atan2
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.world.WorldState
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
 * XZ position is linearly interpolated between block centers — curves are approximated as straight
 * chords rather than following the model's actual curve geometry (unverifiable without a live
 * render to compare against). Y is `railBlockPos.y` (each block's real placed floor —
 * [RailTraversal.connectingNeighbor] already steps this to a neighbor built one grid level up/down
 * through a slope) plus [RailTraversal.localHeight]'s smooth within-block ease along the current
 * block's own declared surface.
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
        val height = RailTraversal.localHeight(world, current, dir.opposite, dir, t)
        val base = RailTraversal.baseHeight(world, current)
        instance.pos =
            Vec3(
                current.x + 0.5f + dir.dx * t,
                current.y + base + height,
                current.z + 0.5f + dir.dz * t,
            )
        instance.yaw = atan2(dir.dx.toDouble(), dir.dz.toDouble()).toFloat()
    }
}
