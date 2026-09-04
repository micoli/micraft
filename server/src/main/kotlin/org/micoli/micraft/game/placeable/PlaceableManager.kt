package org.micoli.micraft.game.placeable

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
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.rail.RailConnection
import org.micoli.micraft.placeable.PlaceableState
import org.micoli.micraft.placeable.siege.SiegeWeaponRegistry
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(PlaceableManager::class.java)

/**
 * Spawn/lookup/orientation bookkeeping for free-standing placed objects — mirrors
 * [org.micoli.micraft.game.vehicle.VehicleManager]'s spawn half, minus the rail-bound movement (a
 * placeable never moves once spawned; [org.micoli.micraft.game.placeable.siege.SiegeWeaponManager]
 * composes on top of this for anything siege-specific).
 *
 * There is no longer a generic placeable-definition registry: [SiegeWeaponRegistry] is the only
 * concrete placeable sub-system today, so type validation here is sourced directly from it. If a
 * second sub-system (e.g. furniture) is added later, this should become an injected lookup rather
 * than a second hardcoded registry reference.
 */
class PlaceableManager(private val broadcast: suspend (ServerMessage) -> Unit) {
    private val placeables = ConcurrentHashMap<String, PlaceableInstance>()

    fun getAll(): Collection<PlaceableInstance> = placeables.values

    fun get(id: String): PlaceableInstance? = placeables[id]

    /**
     * Spawns a placeable of [type] on top of the block at [groundPos], or returns null (no-op) if
     * [type] isn't a registered placeable, or the block at [groundPos] isn't valid ground (must be
     * solid, non-liquid, and not a rail block — a placeable is free-standing, not rail-bound).
     */
    suspend fun spawn(
        type: EntityType,
        groundPos: BlockPos,
        world: WorldState
    ): PlaceableInstance? {
        if (SiegeWeaponRegistry.get(type) == null) {
            log.debug("spawn rejected: unknown placeable type {}", type)
            return null
        }
        if (!isValidGround(groundPos, world)) {
            log.debug("spawn rejected: {} isn't valid ground for a placeable", groundPos)
            return null
        }
        val instance =
            PlaceableInstance(
                UUID.randomUUID().toString(),
                type,
                Vec3(groundPos.x + 0.5f, groundPos.y.toFloat(), groundPos.z + 0.5f))
        placeables[instance.id] = instance
        broadcast(ServerMessage.PlaceableSpawned(instance.toState()))
        log.debug("Spawned placeable {} ({}) at {}", instance.id, type, groundPos)
        return instance
    }

    private fun isValidGround(groundPos: BlockPos, world: WorldState): Boolean {
        val below = world.getBlock(groundPos.x, groundPos.y - 1, groundPos.z)
        val def = BlockRegistry.get(below)
        return def.solid && !def.liquid && !RailConnection.isRail(below)
    }

    /** Despawns [id] and returns its item to [session]'s inventory, if it maps to one. */
    suspend fun despawn(id: String, session: PlayerSession) {
        val instance = placeables.remove(id) ?: return
        broadcast(ServerMessage.PlaceableDespawned(id))
        val itemType = itemTypeFor(instance.type)
        if (itemType != null) {
            session.inventory[itemType] = (session.inventory[itemType] ?: 0) + 1
            session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        }
    }

    /** Reverse lookup EntityType -> ItemType, built once per call from [ItemRegistry]. */
    private fun itemTypeFor(type: EntityType): ItemType? =
        ItemRegistry.keys().firstOrNull { ItemRegistry.get(it).spawnsEntity == type }

    /** Generic X-key interaction — despawns (break-down semantics). */
    suspend fun handleInteract(id: String, session: PlayerSession) = despawn(id, session)

    /** Generic R-key rotation — advances rotationStep by one 30° step, wraps 11 -> 0. */
    suspend fun handleRotate(id: String) {
        val instance = placeables[id] ?: return
        instance.rotate()
        broadcast(ServerMessage.PlaceableUpdate(instance.toState()))
    }

    /** Absolute rotationStep set — used by `/siege_weapon rotation <xx>`. */
    suspend fun setRotationStep(id: String, value: Int) {
        val instance = placeables[id] ?: return
        instance.rotateTo(value)
        broadcast(ServerMessage.PlaceableUpdate(instance.toState()))
    }

    /** Replays every currently-spawned placeable to a newly-connected session. */
    suspend fun sendAllTo(session: PlayerSession) {
        for (instance in placeables.values) session.send(
            ServerMessage.PlaceableSpawned(instance.toState()))
    }

    /**
     * Restores spawned placeables from a previous [save] — mirrors VehicleManager's load/save pair.
     */
    fun load(savePath: Path) {
        if (!savePath.exists()) {
            log.info("No placeable save file at {}", savePath)
            return
        }
        runCatching {
                val states =
                    Yaml.default.decodeFromString(
                        ListSerializer(PlaceableState.serializer()), savePath.readText())
                var loaded = 0
                for (state in states) {
                    if (SiegeWeaponRegistry.get(state.placeableType) == null) {
                        log.warn(
                            "Unknown placeable type '{}' in save file — skipped",
                            state.placeableType)
                        continue
                    }
                    val instance = PlaceableInstance(state.id, state.placeableType, state.pos)
                    instance.rotationStep = state.rotationStep
                    placeables[instance.id] = instance
                    loaded++
                }
                log.info("Loaded {} placeables from {}", loaded, savePath)
            }
            .onFailure { e ->
                log.warn("Failed to load placeables from {}: {}", savePath, e.message)
            }
    }

    fun save(savePath: Path) {
        runCatching {
                savePath.parent?.createDirectories()
                val states = placeables.values.map { it.toState() }
                savePath.writeText(
                    Yaml.default.encodeToString(
                        ListSerializer(PlaceableState.serializer()), states))
            }
            .onFailure { e -> log.warn("Failed to save placeables: {}", e.message) }
    }
}
