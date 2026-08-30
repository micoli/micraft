package org.micoli.micraft.game.world

import io.ktor.server.application.Application
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.di.PlayerPersister
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.GameTimePersistence
import org.micoli.micraft.game.GameTimeService
import org.micoli.micraft.game.TICK_SECONDS
import org.micoli.micraft.game.TIME_BROADCAST_TICKS
import org.micoli.micraft.game.auction.AuctionManager
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.RegenProcessor
import org.micoli.micraft.game.combat.StatusEffectProcessor
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcSubsystemFactory
import org.micoli.micraft.game.npc.NpcTickPipeline
import org.micoli.micraft.game.pet.PetManager
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileTickPipeline
import org.micoli.micraft.game.placeable.siege.SiegeWeaponManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.hasPermission
import org.micoli.micraft.game.social.GroupManager
import org.micoli.micraft.game.tick.ChunkStreamer
import org.micoli.micraft.game.tick.IntentCollector
import org.micoli.micraft.game.tick.MovementProcessor
import org.micoli.micraft.game.tick.TickProfiler
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.game.vehicle.VehicleTickPipeline
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.instance.toProto
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.plugin.TickContext
import org.micoli.micraft.plugin.TickHandler
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GameWorld")

private const val TARGET_DISTANCE_REFRESH_TICKS = 5

internal fun String.toPlayerAdminJson() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

/**
 * One isolated world: its [WorldState], its (optional) [WorldPersistence] directory, the players
 * connected to it, its game clock and its per-tick simulation. Production runs a single instance
 * ([DEFAULT_ID]); the E2E harness will run one per `?gameSession=` so parallel browser tests never
 * share terrain or a player set.
 *
 * A9.2 of the GameLoop extraction: this owns the world identity, the persisted-state lifecycle, the
 * clock and `tickBody`. The per-world subsystems are still constructed by `GameLoop` and handed in;
 * later steps move that construction here and move connect handling in.
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
    private val npcTickPipeline: NpcTickPipeline,
    private val vehicleTickPipeline: VehicleTickPipeline,
    private val siegeProjectileTickPipeline: SiegeProjectileTickPipeline,
    private val siegeProjectileManager: SiegeProjectileManager,
    private val petManager: PetManager,
    private val tradeManager: TradeManager,
    private val groupManager: GroupManager,
    private val playerPersister: PlayerPersister,
    private val intentCollectorProvider: () -> IntentCollector,
    private val blockBreaker: BlockBreaker,
    private val movementProcessor: MovementProcessor,
    private val chunkStreamer: ChunkStreamer,
    private val instanceRegistry: InstanceRegistry,
    private val worldItems: WorldItemManager,
    private val combatProcessor: CombatProcessor,
    private val statusEffectProcessor: StatusEffectProcessor,
    private val regenProcessor: RegenProcessor,
    private val weatherManager: WeatherManager,
    private val liquidManager: LiquidManager,
    private val auctionManager: AuctionManager?,
    private val commandContextProvider: () -> CommandContext,
    private val pluginTickHandlersProvider: () -> List<TickHandler>,
    private val broadcastPlayerAdmin: suspend (String) -> Unit,
    private val appScope: () -> Application?,
) {
    var worldMeta: WorldMetadata? = persistence?.loadMetadata()
        private set

    var gameTicks: Long = worldMeta?.gameTicks ?: 18_000L

    val tickProfiler = TickProfiler()

    fun getTickProfile() = tickProfiler.snapshot()

    private var timeBroadcastCounter = 0
    private var targetDistanceTickCounter = 0
    private var npcLifecycleTickCounter = 0

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

    /**
     * Register [session] in this world and send it the state of everyone/everything already here,
     * plus cross-send its arrival. Called once the connect handshake has succeeded.
     */
    suspend fun onPlayerJoin(session: PlayerSession) {
        val id = session.id
        sessions
            .all()
            .filter { it.id != id }
            .forEach { other ->
                session.send(ServerMessage.PlayerUpdate(other.state))
                other.send(ServerMessage.PlayerUpdate(session.state))
            }
        npcManager.sendAllTo(session)
        petManager.rosterSyncFor(session)
        vehicleManager.sendAllTo(session)
        placeableManager.sendAllTo(session)
        siegeWeaponManager.sendAllTo(session)
        siegeProjectileManager.sendAllTo(session)
        // A stale session with the same id (e.g. a second client reconnecting under the same
        // playerName before the first socket's read loop noticed it's dead) would otherwise be
        // silently overwritten in the registry, leaving its socket alive but never ticked again.
        sessions[id]?.let { existing ->
            if (existing !== session) {
                runCatching {
                    existing.socket.close(
                        CloseReason(CloseReason.Codes.VIOLATED_POLICY, "replaced by new session"))
                }
            }
        }
        sessions[id] = session
        val pos = session.state.pos
        broadcastPlayerAdmin(
            """{"type":"playerJoined","id":"$id","name":${session.state.name.toPlayerAdminJson()},"x":${pos.x},"y":${pos.y},"z":${pos.z},"yaw":0.0}""")
    }

    /**
     * Tear down [session]'s footprint in this world — unless a newer reconnect already replaced it.
     */
    suspend fun onPlayerLeave(session: PlayerSession) {
        val id = session.id
        // A session evicted by a newer reconnect under the same id (see onPlayerJoin) must not tear
        // down the state of the session that replaced it.
        if (sessions[id] !== session) {
            log.info(
                "stale session closed: {} name={} (replaced by newer session)",
                id.take(8),
                session.state.name)
            return
        }
        broadcastPlayerAdmin("""{"type":"playerLeft","id":"$id"}""")
        sessions.remove(id)
        chunkStreamer.cleanupSession(id)
        petManager.onPlayerDisconnected(session)
        npcManager.clearPlayer(id)
        vehicleManager.clearRider(id)
        npcTickPipeline.onPlayerDisconnected(sessions.all())
        tradeManager.onPlayerDisconnect(id)
        auctionManager?.clearFilter(id)
        groupManager.onDisconnect(session)
        playerPersister.save(session)
        log.info(
            "player disconnected: {} name={} (total={})",
            id.take(8),
            session.state.name,
            sessions.size)
        val left = ServerMessage.PlayerLeft(id)
        sessions.all().forEach { it.send(left) }
    }

    suspend fun tick(): Unit = tickProfiler.measure("total") { tickBody() }

    private suspend fun tickBody() {
        gameTicks++
        timeBroadcastCounter++
        if (timeBroadcastCounter >= TIME_BROADCAST_TICKS) {
            timeBroadcastCounter = 0
            val timeMsg = ServerMessage.TimeUpdate(gameTicks)
            sessions.all().forEach { it.send(timeMsg) }
        }

        val intentCollector = intentCollectorProvider()
        tickProfiler.measure("players") {
            sessions.all().forEach { session ->
                val input = intentCollector.collect(session)
                blockBreaker.tick(session)
                val newState = movementProcessor.process(session, input)
                if (newState != session.state) {
                    session.state = newState
                    val update = ServerMessage.PlayerUpdate(newState, session.lastProcessedSeq)
                    sessions.all().forEach { it.send(update) }
                    broadcastPlayerAdmin(
                        """{"type":"playerMoved","id":"${session.id}","name":${newState.name.toPlayerAdminJson()},"x":${newState.pos.x},"y":${newState.pos.y},"z":${newState.pos.z},"yaw":${newState.orientation.yaw}}""")
                }
                if (session.chunkMode == "websocket") {
                    chunkStreamer.checkAndRequest(session)
                    chunkStreamer.deliverReady(session)
                }
                val (newZoneX, newZoneZ) =
                    npcTickPipeline.zoneOf(session.state.pos.x, session.state.pos.z)
                val lastZone = session.lastZonePos
                if (lastZone == null || lastZone.first != newZoneX || lastZone.second != newZoneZ) {
                    session.lastZonePos = Pair(newZoneX, newZoneZ)
                    npcTickPipeline.onZoneCrossed(world, newZoneX, newZoneZ)
                }
                if (session.hasPermission("admin")) {
                    val pos = session.state.pos
                    val instanceZone =
                        instanceRegistry.zoneAt(pos.x.toInt(), pos.y.toInt(), pos.z.toInt())
                    if (instanceZone?.id != session.lastInstanceZoneId) {
                        session.lastInstanceZoneId = instanceZone?.id
                        session.send(ServerMessage.AdminZoneWireframe(instanceZone?.toProto()))
                    }
                }
            }
        }
        tickProfiler.measure("worldItems") { worldItems.tickCollection(sessions.all()) }
        gameTimeService.tick(TICK_SECONDS.toDouble())
        tickProfiler.measure("npc") { npcTickPipeline.tick(world, sessions.all(), combatProcessor) }
        tickProfiler.measure("vehicles") { vehicleTickPipeline.tick(world, sessions.all()) }
        tickProfiler.measure("siegeProjectiles") {
            siegeProjectileTickPipeline.tick(world, sessions.all(), npcManager, combatProcessor)
        }
        // In the tick, not in a wall-clock coroutine of its own: driving the slow lane from a
        // separate 5 s loop raced the main tick and gave the same arena a different spawn rate
        // depending on whether the live server or the simulator was running it.
        npcLifecycleTickCounter++
        if (npcLifecycleTickCounter >= NpcSubsystemFactory.LIFECYCLE_INTERVAL_TICKS) {
            npcLifecycleTickCounter = 0
            tickProfiler.measure("npcLifecycle") {
                runCatching { npcTickPipeline.lifecycle(world, sessions.all()) }
                    .onFailure { log.error("npc lifecycle error: {}", it.message, it) }
            }
        }
        tickProfiler.measure("statusEffects") { statusEffectProcessor.tick(sessions.all()) }
        tickProfiler.measure("regen") { regenProcessor.tick(sessions.all()) }
        tickProfiler.measure("weather") {
            weatherManager.tick(world) { msg -> sessions.all().forEach { it.send(msg) } }
        }
        tickProfiler.measure("liquid") {
            liquidManager.tick { msg -> sessions.all().forEach { it.send(msg) } }
        }
        tickProfiler.measure("vegetation") {
            vegetationManager.tick { msg -> sessions.all().forEach { it.send(msg) } }
        }
        tickProfiler.measure("auction") { auctionManager?.tick() }
        targetDistanceTickCounter++
        if (targetDistanceTickCounter >= TARGET_DISTANCE_REFRESH_TICKS) {
            targetDistanceTickCounter = 0
            sessions.all().forEach { session ->
                if (session.combatState.targetId != null) {
                    session.send(combatProcessor.buildTargetUpdate(session))
                }
            }
        }
        val pluginTickHandlers = pluginTickHandlersProvider()
        if (pluginTickHandlers.isNotEmpty()) {
            val ctx =
                TickContext(
                    gameTicks = gameTicks,
                    sessionRegistry = sessions,
                    world = world,
                    commandContext = commandContextProvider(),
                )
            tickProfiler.measure("plugins") {
                pluginTickHandlers.forEach { handler ->
                    runCatching { handler.tick(ctx) }
                        .onFailure {
                            log.error("TickHandler {} error: {}", handler.name, it.message, it)
                        }
                }
            }
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
