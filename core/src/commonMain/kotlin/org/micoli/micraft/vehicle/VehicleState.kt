package org.micoli.micraft.vehicle

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.player.Vec3

/**
 * Network-facing snapshot of a rail vehicle — mirrors [org.micoli.micraft.npc.NpcState]'s role for
 * NPCs, but without any of the combat/aggro/xp fields that don't apply to a rail-bound vehicle.
 */
@Serializable
data class VehicleState(
    val id: String,
    val vehicleType: EntityType,
    val pos: Vec3,
    val yaw: Float,
    val pitch: Float = 0f,
    val railBlockPos: BlockPos,
    val progress: Float = 0f,
    val direction: Int = 1,
    val moving: Boolean = false,
)
