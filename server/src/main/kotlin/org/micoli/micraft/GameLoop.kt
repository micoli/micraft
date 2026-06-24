package org.micoli.micraft

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.npc.NpcManager
import org.micoli.micraft.npc.NpcSpawner
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.BlockInfo
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.CommandInfo
import org.micoli.micraft.protocol.ItemInfo
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.tick.BlockBreaker
import org.micoli.micraft.tick.BlockPlacer
import org.micoli.micraft.tick.ChunkStreamer
import org.micoli.micraft.tick.IntentCollector
import org.micoli.micraft.tick.MovementProcessor
import org.micoli.micraft.ui.validateLayouts
import org.micoli.micraft.world.BlockRegistry
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChatChannelManager
import org.micoli.micraft.world.ChatService
import org.micoli.micraft.world.ChunkGenerator
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.DropConfig
import org.micoli.micraft.world.I18nConfig
import org.micoli.micraft.world.ItemRegistry
import org.micoli.micraft.world.ItemType
import org.micoli.micraft.world.NpcRegistryLoader
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldMetadata
import org.micoli.micraft.world.WorldPersistence
import org.micoli.micraft.world.WorldState
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GameLoop")

fun discoverCommandHandlers(): Map<String, CommandHandler> =
    io.github.classgraph
        .ClassGraph()
        .enableClassInfo()
        .acceptPackages("org.micoli.micraft")
        .scan()
        .use { result ->
            result
                .getClassesImplementing(CommandHandler::class.java)
                .filter { !it.isAbstract && !it.isInterface }
                .mapNotNull { info ->
                    runCatching {
                            @Suppress("UNCHECKED_CAST")
                            (info.loadClass() as Class<CommandHandler>)
                                .getDeclaredConstructor()
                                .newInstance()
                        }
                        .onFailure { e ->
                            log.warn("Failed to load command handler {}: {}", info.name, e.message)
                        }
                        .getOrNull()
                }
                .associateBy { it.command }
        }

fun discoverPlugins(): List<Plugin> =
    io.github.classgraph
        .ClassGraph()
        .enableClassInfo()
        .acceptPackages("org.micoli.micraft")
        .scan()
        .use { result ->
            result
                .getClassesImplementing(Plugin::class.java)
                .filter { !it.isAbstract && !it.isInterface }
                .mapNotNull { info ->
                    runCatching {
                            @Suppress("UNCHECKED_CAST")
                            (info.loadClass() as Class<Plugin>)
                                .getDeclaredConstructor()
                                .newInstance()
                        }
                        .onFailure { e ->
                            log.warn("Failed to load plugin {}: {}", info.name, e.message)
                        }
                        .getOrNull()
                }
        }

fun validatePluginSystemIds(commands: Map<String, CommandHandler>, plugins: List<Plugin>) {
    val commandDupes = commands.values.groupBy { it.id }.filter { it.value.size > 1 }
    if (commandDupes.isNotEmpty()) {
        val detail =
            commandDupes.entries.joinToString("; ") { (id, cmds) ->
                "$id → ${cmds.joinToString(", ") { it.command }}"
            }
        error("Duplicate command UUIDs detected: $detail")
    }

    val pluginDupes = plugins.groupBy { it.id }.filter { it.value.size > 1 }
    if (pluginDupes.isNotEmpty()) {
        val detail =
            pluginDupes.entries.joinToString("; ") { (id, ps) ->
                "$id → ${ps.joinToString(", ") { it.name }}"
            }
        error("Duplicate plugin UUIDs detected: $detail")
    }
}

fun buildI18nDirs(base: Path = Path.of("data/i18n")): List<Path> {
    val pluginsRoot = Path.of("plugins")
    val pluginDirs =
        if (pluginsRoot.toFile().exists())
            pluginsRoot
                .toFile()
                .listFiles { f -> f.isDirectory }
                ?.map { pluginsRoot.resolve(it.name).resolve("data/i18n") }
                ?.filter { it.toFile().exists() } ?: emptyList()
        else emptyList()
    return listOf(base) + pluginDirs
}

class GameLoop(
    private val world: WorldState,
    private val persistence: WorldPersistence? = null,
    private val reloadBiomes: (() -> ChunkGenerator)? = null,
    private val reloadRegistries: (() -> Unit)? = null,
    val i18n: I18nConfig = I18nConfig(buildI18nDirs()),
) {
    private val sessions = ConcurrentHashMap<String, PlayerSession>()
    private var saveTickCounter = 0
    private var timeBroadcastCounter = 0

    private var worldMeta: WorldMetadata? = persistence?.loadMetadata()
    private var gameTicks: Long = worldMeta?.gameTicks ?: 18_000L

    private val commands: Map<String, CommandHandler> = discoverCommandHandlers()

    private val chatChannelManager = ChatChannelManager()
    private val chatService = ChatService(chatChannelManager, ::savePlayer, { sessions.values })

    private val dropConfig = DropConfig(Path.of("data/drops/drops.yaml"))
    private val worldItems =
        WorldItemManager(
            dropConfig,
            broadcast = { msg -> sessions.values.forEach { it.send(msg) } },
            savePlayer = ::savePlayer,
            i18n = i18n,
        )

    private val npcRegistryLoader = NpcRegistryLoader(Path.of("data/npcs/npcs.yaml"))
    private val npcManager =
        NpcManager(
            broadcast = { msg -> sessions.values.forEach { it.send(msg) } },
            getSessions = { sessions.values },
        )
    private val npcSpawner = NpcSpawner()
    private var npcSpawnTickCounter = 0

    private val commandContext =
        CommandContext(
            world = world,
            persistence = persistence,
            i18n = i18n,
            broadcast = { msg -> sessions.values.forEach { it.send(msg) } },
            sessions = { sessions.values },
            kickSession = { playerName ->
                sessions.values
                    .find { it.state.name == playerName }
                    ?.socket
                    ?.close(CloseReason(CloseReason.Codes.NORMAL, "Kicked by server"))
            },
            reloadConfig = ::reload,
            commands = { commands.values },
            savePlayer = ::savePlayer,
            worldItems = worldItems,
            npcManager = npcManager,
            getGameTime = { gameTicks },
            setGameTime = { gameTicks = it },
            refetchChunks = { session ->
                session.loadedChunks.clear()
                session.inFlightChunks.clear()
                session.lastChunkPos = null
                val cx = Math.floorDiv(session.state.pos.x.toInt(), WorldConstants.CHUNK_SIZE)
                val cz = Math.floorDiv(session.state.pos.z.toInt(), WorldConstants.CHUNK_SIZE)
                chunkStreamer.requestAround(session, cx, cz)
            },
            flushWorld = ::flushWorld,
            chatService = chatService,
            chatChannelManager = chatChannelManager,
        )
    private val blockBreaker =
        BlockBreaker(world, { msg -> sessions.values.forEach { it.send(msg) } }, worldItems)
    private val blockPlacer =
        BlockPlacer(world, { msg -> sessions.values.forEach { it.send(msg) } }, ::savePlayer)
    private val intentCollector =
        IntentCollector(
            blockBreaker,
            blockPlacer,
            ::handleCommand,
            onChatSend = { session, channel, text ->
                chatService.routeMessage(session, channel, text)
            },
        )
    private val movementProcessor = MovementProcessor(world)
    private val chunkStreamer = ChunkStreamer(world)

    val terrainCache = TerrainCache()
    @Volatile private var appScope: Application? = null

    fun getPlayerStates(): List<PlayerState> = sessions.values.map { it.state }

    fun getNpcStates(): List<org.micoli.micraft.npc.NpcState> = npcManager.getAll().map { it.state }

    fun getGameTicks(): Long = gameTicks

    private fun flushWorld() {
        world.flushDirty()
        launchTerrainRebuild()
    }

    private fun launchTerrainRebuild() {
        val scope = appScope ?: return
        val chunks = world.discoveredChunks().mapNotNull { world.getChunkIfDiscovered(it) }
        scope.launch(Dispatchers.IO) {
            terrainCache.rebuild(chunks)
            persistence?.let { terrainCache.save(it.worldDir.resolve("terrain_cache.json")) }
        }
    }

    private fun rebuildTerrainSync() {
        val chunks = world.discoveredChunks().mapNotNull { world.getChunkIfDiscovered(it) }
        terrainCache.rebuild(chunks)
        persistence?.let { terrainCache.save(it.worldDir.resolve("terrain_cache.json")) }
    }

    private fun buildPreferencesSync(session: PlayerSession): ServerMessage.PreferencesSync {
        val knownChannels = chatChannelManager.listKnownChannels()
        val commandList =
            commands.values.map {
                CommandInfo(it.id.toString(), it.command, it.description, it.autocompleteArgs)
            }
        return ServerMessage.PreferencesSync(
            subscribedChannels = session.state.subscribedChannels,
            knownChannels = knownChannels,
            disabledCommands = session.state.disabledCommands,
            shadersEnabled = session.state.shadersEnabled,
            commands = commandList,
        )
    }

    private suspend fun handlePreferencesUpdate(
        session: PlayerSession,
        msg: ClientMessage.PreferencesUpdate
    ) {
        val knownChannels = chatChannelManager.listKnownChannels()
        val newSubscribed =
            (msg.subscribedChannels.filter { it in knownChannels } + ChatChannelManager.PROTECTED)
                .distinct()
        val shadersChanged = session.state.shadersEnabled != msg.shadersEnabled
        session.state =
            session.state.copy(
                subscribedChannels = newSubscribed,
                disabledCommands = msg.disabledCommands,
                shadersEnabled = msg.shadersEnabled,
            )
        savePlayer(session)
        session.send(ServerMessage.ChannelsSync(newSubscribed, knownChannels))
        if (shadersChanged) session.send(ServerMessage.ShadersUpdate(msg.shadersEnabled))
    }

    private fun buildRegistrySync(): ServerMessage.RegistrySync {
        val blocks =
            BlockRegistry.orderedList().mapIndexed { i, def ->
                val name = BlockType.entries[i].name
                BlockInfo(
                    name = name,
                    hardness = def.hardness,
                    solid = def.solid,
                    transparent = def.transparent,
                    minimapColor = def.minimapColor,
                    modelElement = def.modelElement,
                )
            }
        val items =
            ItemType.entries.associate { type ->
                val def = ItemRegistry.get(type)
                type.name to
                    ItemInfo(buildable = def.buildable, placesBlock = def.placesBlock?.name)
            }
        val npcs = npcManager.getDefinitions().map { (key, def) -> key to def.bbmodelFile }.toMap()
        return ServerMessage.RegistrySync(blocks, items, npcs)
    }

    private suspend fun reload(): String {
        val lines = mutableListOf<String>()
        val dropCount = dropConfig.reload()
        lines += "Drops: $dropCount block types"
        if (reloadBiomes != null) {
            world.generator = reloadBiomes.invoke()
            lines += "Biomes reloaded"
        }
        if (reloadRegistries != null) {
            reloadRegistries.invoke()
            val registrySync = buildRegistrySync()
            sessions.values.forEach { it.send(registrySync) }
            lines += "Block/item registry reloaded"
        }
        npcManager.reloadDefinitions(npcRegistryLoader.reload())
        lines += "NPC registry reloaded"
        i18n.reload()
        lines += "i18n: ${i18n.locales.size} locales"
        return lines.joinToString(", ")
    }

    fun start(app: Application) {
        appScope = app
        log.info("GameLoop starting (tick=${TICK_MS}ms, gravity=$GRAVITY)")
        validatePluginSystemIds(commands, discoverPlugins())
        npcManager.loadDefinitions(npcRegistryLoader.load())
        val npcSavePath =
            persistence?.worldDir?.resolve("npcs.json") ?: Path.of("data/npcs/spawns.json")
        npcManager.load(npcSavePath)
        persistence?.let {
            terrainCache.prewarm(
                chunksDir = it.worldDir.resolve("chunks"),
                cacheFile = it.worldDir.resolve("terrain_cache.json"),
            )
        }
        app.launch {
            val npcSavePath =
                persistence?.worldDir?.resolve("npcs.json") ?: Path.of("data/npcs/spawns.json")
            while (isActive) {
                delay(TICK_MS)
                tick()
                saveTickCounter++
                if (saveTickCounter >= SAVE_INTERVAL_TICKS) {
                    saveTickCounter = 0
                    flushWorld()
                    worldMeta?.let { persistence?.saveMetadata(it.copy(gameTicks = gameTicks)) }
                    npcManager.save(npcSavePath)
                }
            }
        }
    }

    fun shutdown() {
        world.flushDirty()
        rebuildTerrainSync()
        worldMeta?.let { persistence?.saveMetadata(it.copy(gameTicks = gameTicks)) }
        sessions.values.forEach { session -> savePlayer(session) }
        val npcSavePath =
            persistence?.worldDir?.resolve("npcs.json") ?: Path.of("data/npcs/spawns.json")
        npcManager.save(npcSavePath)
        log.info("World saved on shutdown")
    }

    private fun savePlayer(session: PlayerSession) {
        persistence?.savePlayerState(
            session.state.name,
            session.state.copy(
                inventory = session.inventory.toMap(),
                shortcutBar = session.shortcutBar.toList(),
            ),
        )
    }

    private suspend fun handleLayoutUpdate(
        session: PlayerSession,
        msg: ClientMessage.LayoutUpdate
    ) {
        val error = validateLayouts(msg.layouts, msg.activeLayout)
        if (error != null) {
            session.send(ServerMessage.Notification(error))
            return
        }
        session.state = session.state.copy(layouts = msg.layouts, activeLayout = msg.activeLayout)
        savePlayer(session)
    }

    private suspend fun tick() {
        gameTicks++
        timeBroadcastCounter++
        if (timeBroadcastCounter >= TIME_BROADCAST_TICKS) {
            timeBroadcastCounter = 0
            val timeMsg = ServerMessage.TimeUpdate(gameTicks)
            sessions.values.forEach { it.send(timeMsg) }
        }

        sessions.values.forEach { session ->
            val input = intentCollector.collect(session)
            blockBreaker.tick(session)
            val newState = movementProcessor.process(session, input)
            if (newState != session.state) {
                session.state = newState
                val update = ServerMessage.PlayerUpdate(newState)
                sessions.values.forEach { it.send(update) }
            }
            chunkStreamer.checkAndRequest(session)
            chunkStreamer.deliverReady(session)
        }
        worldItems.tickCollection(sessions.values)
        npcManager.tick(world)
        npcSpawnTickCounter++
        if (npcSpawnTickCounter >= org.micoli.micraft.npc.NpcConstants.SPAWN_CHECK_INTERVAL_TICKS) {
            npcSpawnTickCounter = 0
            npcSpawner.trySpawn(
                world, npcManager, npcManager.getDefinitions(), world.discoveredChunks())
        }
    }

    private suspend fun handleCommand(session: PlayerSession, text: String) {
        val trimmed = text.trim()
        val name = trimmed.substringBefore(' ').lowercase()
        val args = trimmed.substringAfter(' ', "")
        val handler = commands[name]
        if (handler != null) {
            if (handler.id.toString() in session.state.disabledCommands) {
                session.send(
                    ServerMessage.Notification(
                        i18n.t(session.state.language, "preferences:server:command_disabled")))
                return
            }
            handler.execute(session, args, commandContext)
        } else
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "commands:server:unknown", trimmed)))
    }

    suspend fun autocomplete(
        commandId: String,
        argIndex: Int,
        partial: String,
        playerName: String
    ): List<String> {
        val handler = commands.values.find { it.id.toString() == commandId } ?: return emptyList()
        if (argIndex !in handler.autocompleteArgs) return emptyList()
        val session = sessions.values.find { it.state.name == playerName }
        return handler.completeArg(argIndex, partial, session, commandContext)
    }

    suspend fun onConnect(socket: DefaultWebSocketSession) {
        val id = UUID.randomUUID().toString()

        val connectMsg =
            runCatching {
                    val firstFrame = socket.incoming.receive()
                    if (firstFrame is Frame.Text) {
                        val msg = Json.decodeFromString<ClientMessage>(firstFrame.readText())
                        if (msg is ClientMessage.Connect) msg else null
                    } else null
                }
                .getOrNull()
        val playerName = connectMsg?.playerName ?: "Player"
        val userName = connectMsg?.userName ?: playerName
        val preferredLanguage =
            connectMsg?.preferredLanguage?.let { if (it in i18n.locales) it else "en" } ?: "en"

        val saved = persistence?.loadPlayerState(playerName)
        val spawn = saved?.pos ?: Vec3(SPAWN_X, SPAWN_Y, SPAWN_Z)
        val language =
            saved?.language?.let { if (it in i18n.locales) it else "en" } ?: preferredLanguage
        val shadersEnabled = saved?.shadersEnabled ?: true
        val state =
            PlayerState(
                id = id,
                name = playerName,
                pos = spawn,
                orientation = saved?.orientation ?: Orientation(0f, 0f),
                stance = saved?.stance ?: PlayerStance.STANDING,
                flying = saved?.flying ?: DEBUG_WORLD,
                speedMultiplier = saved?.speedMultiplier ?: 1f,
                language = language,
                shadersEnabled = shadersEnabled,
                layouts = saved?.layouts ?: listOf(org.micoli.micraft.ui.defaultLayout()),
                activeLayout = saved?.activeLayout ?: "default",
                subscribedChannels = saved?.subscribedChannels ?: listOf("world", "system", "game"),
                disabledCommands = saved?.disabledCommands ?: emptySet(),
            )
        val session = PlayerSession(id, userName, socket, state)
        saved?.inventory?.forEach { (type, count) -> session.inventory[type] = count }
        saved?.shortcutBar?.forEachIndexed { i, item ->
            if (i in 0..9) session.shortcutBar[i] = item
        }
        sessions[id] = session
        log.info(
            "player connected: {} name={} user={} (total={})",
            id.take(8),
            playerName,
            userName,
            sessions.size)

        session.send(
            ServerMessage.Welcome(
                id,
                playerName,
                spawn,
                language,
                shadersEnabled,
                session.state.layouts,
                session.state.activeLayout))
        session.send(buildRegistrySync())
        session.send(buildPreferencesSync(session))
        chatService.onPlayerConnect(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(ServerMessage.ShortcutBarUpdate(session.shortcutBar.toList()))
        session.send(ServerMessage.TimeUpdate(gameTicks))

        val spawnCp =
            ChunkPos(
                Math.floorDiv(spawn.x.toInt(), WorldConstants.CHUNK_SIZE),
                Math.floorDiv(spawn.z.toInt(), WorldConstants.CHUNK_SIZE),
            )
        session.lastChunkPos = spawnCp
        chunkStreamer.requestAround(session, spawnCp.cx, spawnCp.cz)
        log.info("chunk requests queued for {}", id.take(8))

        sessions.values
            .filter { it.id != id }
            .forEach { other ->
                session.send(ServerMessage.PlayerUpdate(other.state))
                other.send(ServerMessage.PlayerUpdate(state))
            }
        npcManager.sendAllTo(session)

        try {
            socket.incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    runCatching { Json.decodeFromString<ClientMessage>(frame.readText()) }
                        .onFailure { log.warn("bad frame from {}: {}", id.take(8), it.message) }
                        .getOrNull()
                        ?.let { msg ->
                            when (msg) {
                                is ClientMessage.Disconnect -> return@consumeEach
                                is ClientMessage.ChunkUnload -> {
                                    msg.positions.forEach { session.loadedChunks.remove(it) }
                                    log.debug(
                                        "{} chunks unloaded by {}",
                                        msg.positions.size,
                                        session.id.take(8))
                                }
                                is ClientMessage.LayoutUpdate -> handleLayoutUpdate(session, msg)
                                is ClientMessage.PreferencesUpdate ->
                                    handlePreferencesUpdate(session, msg)
                                is ClientMessage.NpcInteract ->
                                    npcManager.handleInteract(session, msg.npcId)
                                else -> session.intents.trySend(msg)
                            }
                        }
                }
            }
        } finally {
            sessions.remove(id)
            chunkStreamer.cleanupSession(id)
            npcManager.clearPlayer(id)
            savePlayer(session)
            log.info(
                "player disconnected: {} name={} (total={})",
                id.take(8),
                session.state.name,
                sessions.size)
            val left = ServerMessage.PlayerLeft(id)
            sessions.values.forEach { it.send(left) }
        }
    }

    suspend fun onChunkConnect(socket: DefaultWebSocketSession) {
        val playerId =
            runCatching {
                    val frame = socket.incoming.receive()
                    if (frame is Frame.Text) frame.readText().trim() else null
                }
                .getOrNull() ?: return
        val session = sessions[playerId] ?: return
        session.chunkSocket = socket
        log.info("chunk socket attached for {}", playerId.take(8))
        try {
            for (frame in socket.incoming) {
                /* client sends nothing on chunk socket */
            }
        } finally {
            session.chunkSocket = null
            log.info("chunk socket detached for {}", playerId.take(8))
        }
    }
}
