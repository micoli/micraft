package org.micoli.micraft.game.vehicle

import kotlin.math.atan2
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.rail.RailConnection
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
 * render to compare against). Y is pinned to the current block's Y — slopes advance without a
 * smooth climb/descent (RailNetworkRegistry's neighbor lookup is also same-Y-only, see its doc).
 * Both are acceptable v1 gaps: connectivity and direction logic are correct regardless.
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
        updatePose(instance)
        return true
    }

    private fun advanceOneBlock(instance: VehicleInstance, world: WorldState) {
        val currentPos = instance.railBlockPos
        val exitDir = instance.travelDirection
        val nextPos = BlockPos(currentPos.x + exitDir.dx, currentPos.y, currentPos.z + exitDir.dz)
        val nextType = world.getBlock(nextPos.x, nextPos.y, nextPos.z)
        val arrivalDir = exitDir.opposite
        if (!RailConnection.isRail(nextType)) {
            instance.travelDirection = arrivalDir
            return
        }
        val nextState = world.getBlockState(nextPos.x, nextPos.y, nextPos.z)
        val nextExtra = world.getExtraState(nextPos.x, nextPos.y, nextPos.z)
        val nextActive = RailConnection.active(nextType, nextState, nextExtra)
        if (arrivalDir !in nextActive) {
            // Neighbor cell is a rail block but not oriented to connect back — dead end in place.
            instance.travelDirection = arrivalDir
            return
        }
        val forward = (nextActive - arrivalDir).firstOrNull()
        instance.railBlockPos = nextPos
        instance.travelDirection = forward ?: arrivalDir
    }

    private fun updatePose(instance: VehicleInstance) {
        val current = instance.railBlockPos
        val dir = instance.travelDirection
        val t = instance.progress
        instance.pos =
            Vec3(
                current.x + 0.5f + dir.dx * t,
                current.y + 1f,
                current.z + 0.5f + dir.dz * t,
            )
        instance.yaw = atan2(dir.dx.toDouble(), dir.dz.toDouble()).toFloat()
    }
}
