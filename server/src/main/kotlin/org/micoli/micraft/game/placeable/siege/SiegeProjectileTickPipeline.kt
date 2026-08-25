package org.micoli.micraft.game.placeable.siege

import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState

/**
 * Single owner of the siege projectile tick sequence — mirrors
 * [org.micoli.micraft.game.vehicle.VehicleTickPipeline]'s role/doc-comment convention. Don't call
 * `siegeProjectileManager.tick` from anywhere else.
 */
class SiegeProjectileTickPipeline(private val siegeProjectileManager: SiegeProjectileManager) {
    suspend fun tick(
        world: WorldState,
        sessions: Collection<PlayerSession>,
        npcManager: NpcManager,
        combatProcessor: CombatProcessor,
    ) {
        siegeProjectileManager.tick(world, sessions, npcManager, combatProcessor)
    }
}
