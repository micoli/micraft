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
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.RegenProcessor
import org.micoli.micraft.game.combat.StatusEffectProcessor
import org.micoli.micraft.game.mail.MailManager
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcSubsystemFactory
import org.micoli.micraft.game.npc.NpcTickPipeline
import org.micoli.micraft.game.pet.PetManager
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileTickPipeline
import org.micoli.micraft.game.placeable.siege.SiegeWeaponManager
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.hasPermission
import org.micoli.micraft.game.social.FactionManager
import org.micoli.micraft.game.social.GroupManager
import org.micoli.micraft.game.social.GuildManager
import org.micoli.micraft.game.tick.ChunkStreamer
import org.micoli.micraft.game.tick.IntentCollector
import org.micoli.micraft.game.tick.MovementProcessor
import org.micoli.micraft.game.tick.TickProfiler
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.game.vehicle.VehicleTickPipeline
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.claim.ClaimManager
import org.micoli.micraft.game.world.claim.ClaimRegistry
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.instance.toProto
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.rail.RailNetworkRegistry
import org.micoli.micraft.game.world.scene.SceneRegistry
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.player.EditMode
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.rpg.CharacterData
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
    /** Which slices of [tick] actually run — [TickSection.REALTIME] for the default world. */
    val tickSections: Set<TickSection> = TickSection.REALTIME,
    /** Edit mode a joining player is placed in — GAME everywhere; CREATIVE only via `/mode`. */
    val spawnEditMode: EditMode = EditMode.GAME,
    private val terrainCache: TerrainCache,
    val npcManager: NpcManager,
    val vehicleManager: VehicleManager,
    val placeableManager: PlaceableManager,
    val siegeWeaponManager: SiegeWeaponManager,
    val vegetationManager: VegetationManager,
    val npcSubsystem: org.micoli.micraft.game.npc.NpcSubsystem,
    private val vehicleTickPipeline: VehicleTickPipeline,
    private val siegeProjectileTickPipeline: SiegeProjectileTickPipeline,
    val siegeProjectileManager: SiegeProjectileManager,
    val petManager: PetManager,
    val tradeManager: TradeManager,
    val groupManager: GroupManager,
    val guildManager: GuildManager,
    val guildRegistry: org.micoli.micraft.game.social.GuildRegistry,
    val factionManager: FactionManager,
    val chatService: ChatService,
    val chatChannelManager: ChatChannelManager,
    val experienceProcessor: ExperienceProcessor,
    val questManager: QuestManager?,
    val mailManager: MailManager?,
    val claimManager: ClaimManager,
    val claimRegistry: ClaimRegistry,
    val railNetworkRegistry: RailNetworkRegistry,
    val sceneRegistry: SceneRegistry,
    private val playerPersister: PlayerPersister,
    private val intentCollectorProvider: () -> IntentCollector,
    private val blockBreaker: BlockBreaker,
    private val movementProcessor: MovementProcessor,
    val chunkStreamer: ChunkStreamer,
    val instanceRegistry: InstanceRegistry,
    val worldItems: WorldItemManager,
    val combatProcessor: CombatProcessor,
    private val statusEffectProcessor: StatusEffectProcessor,
    val regenProcessor: RegenProcessor,
    val weatherManager: WeatherManager,
    val liquidManager: LiquidManager,
    val auctionManager: AuctionManager?,
    private val commandContextProvider: () -> CommandContext,
    private val pluginTickHandlersProvider: () -> List<TickHandler>,
    /** Extra sink for player-admin events, on top of this world's own listener list. */
    private val broadcastPlayerAdminSink: suspend (String) -> Unit = {},
    private val appScope: () -> Application?,
    /** Where the game clock starts when there is no persisted metadata (a simulator wants 0). */
    private val initialGameTicks: Long? = null,
    /** Extra sink for world changes the tick broadcasts (vegetation regrowth, liquid, weather). */
    private val broadcastWorldChange: suspend (ServerMessage) -> Unit = {},
    /** Gate on the slow NPC lane — a simulator only runs it with a player present. */
    private val npcLifecycleGate: () -> Boolean = { true },
) {
    val gameTimeService: GameTimeService
        get() = npcSubsystem.gameTimeService

    private val npcTickPipeline: NpcTickPipeline
        get() = npcSubsystem.pipeline

    var worldMeta: WorldMetadata? = persistence?.loadMetadata()
        private set

    var gameTicks: Long = worldMeta?.gameTicks ?: initialGameTicks ?: 18_000L

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
        if (TickSection.TIME_BROADCAST in tickSections) {
            timeBroadcastCounter++
            if (timeBroadcastCounter >= TIME_BROADCAST_TICKS) {
                timeBroadcastCounter = 0
                val timeMsg = ServerMessage.TimeUpdate(gameTicks)
                sessions.all().forEach { it.send(timeMsg) }
            }
        }

        if (TickSection.PLAYERS in tickSections) {
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
                    if (lastZone == null ||
                        lastZone.first != newZoneX ||
                        lastZone.second != newZoneZ) {
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
        }
        if (TickSection.WORLD_ITEMS in tickSections) {
            tickProfiler.measure("worldItems") { worldItems.tickCollection(sessions.all()) }
        }
        gameTimeService.tick(TICK_SECONDS.toDouble())

        fullSimulationTick()

        if (TickSection.PLUGINS in tickSections) {
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
    }

    private suspend fun broadcastWorldChangeAndSessions(msg: ServerMessage) {
        broadcastWorldChange(msg)
        sessions.all().forEach { it.send(msg) }
    }

    private suspend fun fullSimulationTick() {
        if (TickSection.NPC in tickSections) {
            tickProfiler.measure("npc") {
                npcTickPipeline.tick(world, sessions.all(), combatProcessor)
            }
        }
        if (TickSection.VEHICLES in tickSections) {
            tickProfiler.measure("vehicles") { vehicleTickPipeline.tick(world, sessions.all()) }
        }
        if (TickSection.SIEGE in tickSections) {
            tickProfiler.measure("siegeProjectiles") {
                siegeProjectileTickPipeline.tick(world, sessions.all(), npcManager, combatProcessor)
            }
        }
        // In the tick, not in a wall-clock coroutine of its own: driving the slow lane from a
        // separate 5 s loop raced the main tick and gave the same arena a different spawn rate
        // depending on whether the live server or the simulator was running it.
        if (TickSection.NPC_LIFECYCLE in tickSections && npcLifecycleGate()) {
            npcLifecycleTickCounter++
            if (npcLifecycleTickCounter >= NpcSubsystemFactory.LIFECYCLE_INTERVAL_TICKS) {
                npcLifecycleTickCounter = 0
                tickProfiler.measure("npcLifecycle") {
                    runCatching { npcTickPipeline.lifecycle(world, sessions.all()) }
                        .onFailure { log.error("npc lifecycle error: {}", it.message, it) }
                }
            }
        }
        if (TickSection.STATUS_EFFECTS in tickSections) {
            tickProfiler.measure("statusEffects") { statusEffectProcessor.tick(sessions.all()) }
        }
        if (TickSection.REGEN in tickSections) {
            tickProfiler.measure("regen") { regenProcessor.tick(sessions.all()) }
        }
        if (TickSection.WEATHER in tickSections) {
            tickProfiler.measure("weather") {
                weatherManager.tick(world) { msg -> broadcastWorldChangeAndSessions(msg) }
            }
        }
        if (TickSection.LIQUID in tickSections) {
            tickProfiler.measure("liquid") {
                liquidManager.tick { msg -> broadcastWorldChangeAndSessions(msg) }
            }
        }
        if (TickSection.VEGETATION in tickSections) {
            tickProfiler.measure("vegetation") {
                vegetationManager.tick { msg -> broadcastWorldChangeAndSessions(msg) }
            }
        }
        if (TickSection.AUCTION in tickSections) {
            tickProfiler.measure("auction") { auctionManager?.tick() }
        }
        if (TickSection.TARGET_DISTANCE in tickSections) {
            targetDistanceTickCounter++
            if (targetDistanceTickCounter >= TARGET_DISTANCE_REFRESH_TICKS) {
                targetDistanceTickCounter = 0
                sessions.all().forEach { session ->
                    if (session.combatState.targetId != null) {
                        session.send(combatProcessor.buildTargetUpdate(session))
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

    private val playerAdminListeners =
        java.util.concurrent.CopyOnWriteArrayList<suspend (String) -> Unit>()

    fun addPlayerAdminListener(listener: suspend (String) -> Unit) {
        playerAdminListeners.add(listener)
    }

    fun removePlayerAdminListener(listener: suspend (String) -> Unit) {
        playerAdminListeners.remove(listener)
    }

    private suspend fun broadcastPlayerAdmin(json: String) {
        broadcastPlayerAdminSink(json)
        for (l in playerAdminListeners) runCatching { l(json) }
    }

    // Filled by `POST /api/admin/players` before the browser connects, keyed by lower-cased player
    // name. `onConnect` consumes it in place of minting a fresh id / running character creation, so
    // an E2E test's RPG player is ready up front (memory-only worlds have no persistence).
    data class ReservedPlayer(val id: String, val characterData: CharacterData?)

    val reservedPlayers = java.util.concurrent.ConcurrentHashMap<String, ReservedPlayer>()

    fun reservePlayer(name: String, characterData: CharacterData? = null): ReservedPlayer =
        reservedPlayers.compute(name.lowercase()) { _, cur ->
            ReservedPlayer(
                cur?.id ?: java.util.UUID.randomUUID().toString(),
                characterData ?: cur?.characterData)
        }!!

    // ── World-scoped admin surface (mirrors GameLoop's accessors so admin routes can target
    //    a specific GameWorld). ────────────────────────────────────────────────────────────
    fun getPlayerStates(): List<PlayerState> = sessions.all().map { it.state }

    fun findSession(name: String): PlayerSession? =
        sessions.all().find { sanitizePlayerName(it.state.name).equals(name, ignoreCase = true) }

    fun savePlayerSession(session: PlayerSession) = playerPersister.save(session)

    suspend fun broadcastPlayerUpdate(session: PlayerSession) =
        sessions.broadcast(ServerMessage.PlayerUpdate(session.state))

    suspend fun broadcastInstanceZonesSync() {
        val msg = ServerMessage.InstanceZonesSync(instanceRegistry.all().map { it.toProto() })
        sessions.all().filter { it.hasPermission("admin") }.forEach { it.send(msg) }
    }

    suspend fun broadcastWorldUpdate(
        changes: List<org.micoli.micraft.protocol.BlockChange>,
        entityAdds: List<org.micoli.micraft.protocol.BlockEntityProto> = emptyList(),
        entityRemoves: List<org.micoli.micraft.game.world.BlockPos> = emptyList(),
        entityRemovesAt: List<org.micoli.micraft.protocol.EntityRemoveAt> = emptyList(),
    ) {
        sessions.broadcast(
            ServerMessage.WorldUpdate(changes, entityAdds, entityRemoves, entityRemovesAt))
    }

    fun getNpcInstances() = npcManager.getAll()

    fun instances() = instanceRegistry

    fun scenes() = sceneRegistry

    fun claims() = claimRegistry

    fun getWorldState() = world

    companion object {
        const val DEFAULT_ID = "default"
    }

    init {
        if (persistence == null) log.debug("GameWorld {} is memory-only", id)
    }
}
