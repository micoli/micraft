package org.micoli.micraft.game.vehicle

import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.vehicle.VehicleState

/**
 * Server-side runtime state of a spawned vehicle — mirrors
 * [org.micoli.micraft.game.npc.NpcInstance]'s role for NPCs. `progress`/`direction` are unused
 * until locomotion lands (server-side tick behavior), but are part of the wire state already so
 * spawning it now doesn't require a second breaking protocol change.
 */
class VehicleInstance(
    val id: String,
    val type: EntityType,
    var railBlockPos: BlockPos,
    var direction: Int = 1,
) {
    var progress: Float = 0f
    var pos: Vec3 = Vec3(railBlockPos.x + 0.5f, railBlockPos.y + 0.5f, railBlockPos.z + 0.5f)
    var yaw: Float = 0f

    fun toState(): VehicleState =
        VehicleState(
            id = id,
            vehicleType = type,
            pos = pos,
            yaw = yaw,
            railBlockPos = railBlockPos,
            progress = progress,
            direction = direction,
        )
}
