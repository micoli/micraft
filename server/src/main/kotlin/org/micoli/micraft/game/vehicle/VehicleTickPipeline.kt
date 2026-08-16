package org.micoli.micraft.game.vehicle

import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState

/**
 * Single owner of the vehicle tick sequence — mirrors
 * [org.micoli.micraft.game.npc.NpcTickPipeline]'s role/doc-comment convention. See
 * `VehicleTickOwnershipTest`: don't call `vehicleManager.tick` from anywhere else.
 */
class VehicleTickPipeline(private val vehicleManager: VehicleManager) {
    suspend fun tick(world: WorldState, sessions: Collection<PlayerSession>) {
        vehicleManager.tick(world, sessions)
    }
}
