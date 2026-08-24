package org.micoli.micraft.game.vehicle

import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.vehicle.VehicleState

/**
 * Server-side runtime state of a spawned vehicle — mirrors
 * [org.micoli.micraft.game.npc.NpcInstance]'s role for NPCs. Position on the network is the block
 * it currently occupies plus the direction it's exiting through and how far along that exit (0..1)
 * — see [VehicleBehavior].
 */
class VehicleInstance(
    val id: String,
    val type: EntityType,
    var railBlockPos: BlockPos,
    var travelDirection: Direction,
) {
    var progress: Float = 0f
    var pos: Vec3 = Vec3(railBlockPos.x + 0.5f, railBlockPos.y + 1f, railBlockPos.z + 0.5f)
    var yaw: Float = 0f
    var pitch: Float = 0f
    var moving: Boolean = false

    /** Server-only runtime linkage — never serialized (a vehicle never reloads with a rider). */
    var riderSessionId: String? = null

    fun toState(): VehicleState =
        VehicleState(
            id = id,
            vehicleType = type,
            pos = pos,
            yaw = yaw,
            pitch = pitch,
            railBlockPos = railBlockPos,
            progress = progress,
            direction = travelDirection.ordinal,
            moving = moving,
        )
}
