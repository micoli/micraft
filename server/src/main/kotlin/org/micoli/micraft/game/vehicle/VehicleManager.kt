package org.micoli.micraft.game.vehicle

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.ListSerializer
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.rail.Direction
import org.micoli.micraft.game.world.rail.RailConnection
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.vehicle.VehicleRegistry
import org.micoli.micraft.vehicle.VehicleState
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("VehicleManager")

/** Matches NpcConfig's default updateRange — vehicles have no tuning config of their own yet. */
private const val UPDATE_RANGE = 96f

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
        initialDirection: Direction,
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

    suspend fun despawnVehicle(id: String) {
        if (vehicles.remove(id) == null) return
        broadcast(ServerMessage.VehicleDespawned(id))
    }

    /** Toggles a vehicle between moving and stopped — the rail equivalent of boarding a mount. */
    suspend fun handleInteract(id: String) {
        val instance = vehicles[id] ?: return
        instance.moving = !instance.moving
        broadcast(ServerMessage.VehicleUpdate(instance.toState()))
    }

    /** Mounts [session] into vehicle [vehicleId]; false if unknown or already occupied. */
    fun mount(vehicleId: String, session: PlayerSession): Boolean {
        val instance = vehicles[vehicleId] ?: return false
        if (instance.riderSessionId != null) return false
        instance.riderSessionId = session.id
        return true
    }

    /** Clears [session] as rider from whichever vehicle it's mounted in, if any. */
    fun dismount(session: PlayerSession) = clearRider(session.id)

    /** Clears [sessionId] as rider from whichever vehicle it's mounted in, if any. */
    fun clearRider(sessionId: String) {
        vehicles.values.firstOrNull { it.riderSessionId == sessionId }?.riderSessionId = null
    }

    /** Replays every currently-spawned vehicle to a newly-connected session. */
    suspend fun sendAllTo(session: PlayerSession) {
        log.debug("sendAllTo {}: {} vehicles in memory", session.id.take(8), vehicles.size)
        for (instance in vehicles.values) session.send(
            ServerMessage.VehicleSpawned(instance.toState()))
    }

    /** Restores spawned vehicles from a previous [save] — mirrors NpcManager's load/save pair. */
    fun load(savePath: Path) {
        if (!savePath.exists()) {
            log.info("No vehicle save file at {}", savePath)
            return
        }
        runCatching {
                val states =
                    Yaml.default.decodeFromString(
                        ListSerializer(VehicleState.serializer()), savePath.readText())
                var loaded = 0
                for (state in states) {
                    if (VehicleRegistry.get(state.vehicleType) == null) {
                        log.warn(
                            "Unknown vehicle type '{}' in save file — skipped", state.vehicleType)
                        continue
                    }
                    val direction = Direction.entries.getOrElse(state.direction) { Direction.NORTH }
                    val instance =
                        VehicleInstance(state.id, state.vehicleType, state.railBlockPos, direction)
                    instance.progress = state.progress
                    instance.pos = state.pos
                    instance.yaw = state.yaw
                    instance.moving = state.moving
                    vehicles[instance.id] = instance
                    loaded++
                }
                log.info("Loaded {} vehicles from {}", loaded, savePath)
            }
            .onFailure { e -> log.warn("Failed to load vehicles from {}: {}", savePath, e.message) }
    }

    fun save(savePath: Path) {
        runCatching {
                savePath.parent?.createDirectories()
                val states = vehicles.values.map { it.toState() }
                savePath.writeText(
                    Yaml.default.encodeToString(ListSerializer(VehicleState.serializer()), states))
            }
            .onFailure { e -> log.warn("Failed to save vehicles: {}", e.message) }
    }

    /** One simulation tick for every spawned vehicle, replicated only to sessions in range. */
    suspend fun tick(world: WorldState, sessions: Collection<PlayerSession>) {
        val rangeSq = UPDATE_RANGE * UPDATE_RANGE
        for (instance in vehicles.values) {
            val moved = VehicleBehavior.tick(instance, world)
            if (moved) {
                val state = instance.toState()
                for (session in sessions) {
                    val dx = session.state.pos.x - state.pos.x
                    val dz = session.state.pos.z - state.pos.z
                    if (dx * dx + dz * dz <= rangeSq)
                        session.send(ServerMessage.VehicleUpdate(state))
                }
            }
            syncRider(instance, sessions)
        }
    }

    /**
     * Keeps the rider's position glued to the vehicle every tick (even when stopped — otherwise a
     * stationary vehicle would let its rider fall through gravity, since [MovementProcessor] fully
     * short-circuits physics while mounted). Orientation is left untouched — riders keep free look.
     */
    private suspend fun syncRider(instance: VehicleInstance, sessions: Collection<PlayerSession>) {
        val riderId = instance.riderSessionId ?: return
        val riderSession = sessions.firstOrNull { it.id == riderId }
        if (riderSession == null || riderSession.mountedVehicleId != instance.id) {
            instance.riderSessionId = null
            return
        }
        val seatOffset = VehicleRegistry.get(instance.type)?.seatOffset ?: Vec3(0f, 0f, 0f)
        riderSession.state =
            riderSession.state.copy(
                pos =
                    Vec3(
                        instance.pos.x + seatOffset.x,
                        instance.pos.y + seatOffset.y,
                        instance.pos.z + seatOffset.z,
                    ))
        broadcast(ServerMessage.PlayerUpdate(riderSession.state, riderSession.lastProcessedSeq))
    }
}
