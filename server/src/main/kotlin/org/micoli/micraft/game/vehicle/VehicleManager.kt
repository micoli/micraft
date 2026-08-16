package org.micoli.micraft.game.vehicle

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.rail.RailConnection
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.vehicle.VehicleRegistry
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("VehicleManager")

/**
 * Spawn/lookup bookkeeping for rail vehicles — mirrors the spawn half of
 * [org.micoli.micraft.game.npc.NpcManager]'s role, without the tick/movement/aggro/visibility
 * machinery an NPC needs (vehicles have no combat, and locomotion lands in a later phase).
 */
class VehicleManager(private val broadcast: suspend (ServerMessage) -> Unit) {
    private val vehicles = ConcurrentHashMap<String, VehicleInstance>()

    fun getAll(): Collection<VehicleInstance> = vehicles.values

    fun get(id: String): VehicleInstance? = vehicles[id]

    /**
     * Spawns a vehicle of [type] on the rail block at [railPos], or returns null (no-op) if [type]
     * isn't a registered vehicle or [railPos] isn't a rail block.
     */
    suspend fun spawnVehicle(
        type: EntityType,
        railPos: BlockPos,
        world: WorldState,
        initialDirection: Int = 1,
    ): VehicleInstance? {
        if (VehicleRegistry.get(type) == null) {
            log.debug("spawnVehicle rejected: unknown vehicle type {}", type)
            return null
        }
        val blockType = world.getBlock(railPos.x, railPos.y, railPos.z)
        if (!RailConnection.isRail(blockType)) {
            log.debug("spawnVehicle rejected: {} at {} isn't a rail block", blockType, railPos)
            return null
        }
        val instance =
            VehicleInstance(UUID.randomUUID().toString(), type, railPos, initialDirection)
        vehicles[instance.id] = instance
        broadcast(ServerMessage.VehicleSpawned(instance.toState()))
        log.debug("Spawned vehicle {} ({}) at {}", instance.id, type, railPos)
        return instance
    }
}
