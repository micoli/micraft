package org.micoli.micraft.game.world

import io.ktor.server.application.Application
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.GameTimePersistence
import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.placeable.siege.SiegeWeaponManager
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.http.TerrainCache
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GameWorld")

/**
 * One isolated world: its [WorldState], its (optional) [WorldPersistence] directory, the players
 * connected to it and its game clock. Production runs a single instance ([DEFAULT_ID]); the E2E
 * harness will run one per `?gameSession=` so parallel browser tests never share terrain or a
 * player set.
 *
 * A9.2 of the GameLoop extraction: this currently owns the world identity, the persisted-state
 * lifecycle and the clock. The per-world subsystems are still constructed by `GameLoop` and handed
 * in; later steps move that construction here and move `tickBody`/connect handling in.
 */
class GameWorld(
    val id: String,
    val world: WorldState,
    val persistence: WorldPersistence?,
    val sessions: SessionRegistry,
    private val terrainCache: TerrainCache,
    private val npcManager: NpcManager,
    private val vehicleManager: VehicleManager,
    private val placeableManager: PlaceableManager,
    private val siegeWeaponManager: SiegeWeaponManager,
    private val vegetationManager: VegetationManager,
    private val gameTimeService: GameTimeService,
    private val appScope: () -> Application?,
) {
    var worldMeta: WorldMetadata? = persistence?.loadMetadata()
        private set

    var gameTicks: Long = worldMeta?.gameTicks ?: 18_000L

    private val npcSavePath: Path
        get() = persistence?.worldDir?.resolve("npcs.yaml") ?: Path.of("data/config/spawns.json")

    private val vehicleSavePath: Path
        get() =
            persistence?.worldDir?.resolve("vehicles.yaml") ?: Path.of("data/config/vehicles.yaml")

    private val placeableSavePath: Path
        get() =
            persistence?.worldDir?.resolve("placeables.yaml")
                ?: Path.of("data/config/placeables_save.yaml")

    /**
     * Load the state that belongs to this world's directory. Global registries are loaded
     * elsewhere.
     */
    fun loadPersistedState() {
        npcManager.load(npcSavePath)
        vehicleManager.load(vehicleSavePath)
        placeableManager.load(placeableSavePath)
        placeableManager.getAll().forEach { siegeWeaponManager.linkFor(it) }
        persistence?.let {
            GameTimePersistence.load(it.worldDir.resolve("game_time.yaml"), gameTimeService)
        }
        vegetationManager.load()
        persistence?.let {
            terrainCache.prewarm(
                chunksDir = it.worldDir.resolve("chunks"),
                cacheDir = it.worldDir.resolve("terrain_cache"),
            )
        }
    }

    /** Flush dirty chunks and persist every per-world manager. */
    fun saveState() {
        world.flushDirty()
        npcManager.save(npcSavePath)
        vehicleManager.save(vehicleSavePath)
        placeableManager.save(placeableSavePath)
        persistence?.let {
            GameTimePersistence.save(it.worldDir.resolve("game_time.yaml"), gameTimeService)
        }
        vegetationManager.save()
    }

    fun saveMetadata() {
        worldMeta?.let { persistence?.saveMetadata(it.copy(gameTicks = gameTicks)) }
    }

    fun launchTerrainRebuild() {
        val scope = appScope() ?: return
        val chunks = world.discoveredChunks().mapNotNull { world.getChunkIfDiscovered(it) }
        scope.launch(Dispatchers.IO) {
            terrainCache.rebuild(chunks)
            persistence?.let { terrainCache.save(it.worldDir.resolve("terrain_cache")) }
        }
    }

    fun rebuildTerrainSync() {
        val chunks = world.discoveredChunks().mapNotNull { world.getChunkIfDiscovered(it) }
        terrainCache.rebuild(chunks)
        persistence?.let { terrainCache.save(it.worldDir.resolve("terrain_cache")) }
    }

    /** Flush terrain cache asynchronously and persist the world — the `/flush` command path. */
    fun flush() {
        launchTerrainRebuild()
        saveState()
    }

    companion object {
        const val DEFAULT_ID = "default"
    }

    init {
        if (persistence == null) log.debug("GameWorld {} is memory-only", id)
    }
}
