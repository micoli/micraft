package org.micoli.micraft.game

import io.github.classgraph.ClassGraph
import io.ktor.server.application.*
import io.ktor.websocket.*
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.auth.AuthProvider
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Plugin
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.di.CommandContextClosures
import org.micoli.micraft.di.PlayerPersister
import org.micoli.micraft.di.ReloadCoordinator
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.ArmorRegistryLoader
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.classes.ClassesConfig
import org.micoli.micraft.game.classes.ClassesConfigData
import org.micoli.micraft.game.combat.AttackConfig
import org.micoli.micraft.game.combat.CombatConfig
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.RegenProcessor
import org.micoli.micraft.game.combat.SpellConfig
import org.micoli.micraft.game.combat.SpellDefinition
import org.micoli.micraft.game.combat.SpellProcessor
import org.micoli.micraft.game.combat.StatusEffectProcessor
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.keybinding.defaultKeyBindings
import org.micoli.micraft.game.macro.MacroContext
import org.micoli.micraft.game.macro.MacroExecutor
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.npc.NpcSpawner
import org.micoli.micraft.game.recipe.RecipeRegistry
import org.micoli.micraft.game.recipe.RecipeRegistryLoader
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.ExperienceConfig
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.game.session.NetworkStats
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.toSlotMap
import org.micoli.micraft.game.tick.ChunkStreamer
import org.micoli.micraft.game.tick.IntentCollector
import org.micoli.micraft.game.tick.MovementProcessor
import org.micoli.micraft.game.trade.TradeConfigLoader
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldMetadata
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.block.BlockPlacer
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.game.world.weather.WeatherConfig
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.ChannelSubscription
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.hasChannel
import org.micoli.micraft.protocol.BlockInfo
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.CommandInfo
import org.micoli.micraft.protocol.ItemInfo
import org.micoli.micraft.protocol.NpcCodexInfo
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.ui.defaultLayout
import org.micoli.micraft.ui.validateLayouts
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GameLoop")

fun discoverCommandHandlers(): Map<String, CommandHandler> =
    ClassGraph().enableClassInfo().acceptPackages("org.micoli.micraft").scan().use { result ->
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
    ClassGraph().enableClassInfo().acceptPackages("org.micoli.micraft").scan().use { result ->
        result
            .getClassesImplementing(Plugin::class.java)
            .filter { !it.isAbstract && !it.isInterface }
            .mapNotNull { info ->
                runCatching {
                        @Suppress("UNCHECKED_CAST")
                        (info.loadClass() as Class<Plugin>).getDeclaredConstructor().newInstance()
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

private const val TARGET_DISTANCE_REFRESH_TICKS = 5

class GameLoop(
    private val world: WorldState,
    private val persistence: WorldPersistence? = null,
    private val reloadBiomes: (() -> ChunkGenerator)? = null,
    private val reloadRegistries: (() -> Unit)? = null,
    private val reloadGameConfig: (() -> Unit)? = null,
    val i18n: I18nConfig = I18nConfig.fromClasspath(pluginsRoot = Path.of("plugins")),
    private val tokenStore: TokenStore? = null,
    private val authProvider: AuthProvider? = null,
    private val groupsConfig: GroupsConfig? = null,
    private val reloadRbac: (() -> Unit)? = null,
    private val chunkSection: ChunkSection = ChunkSection(),
    private val sessionRegistry: SessionRegistry = SessionRegistry(),
    private val playerPersister: PlayerPersister = PlayerPersister(persistence),
    private val chatChannelManager: ChatChannelManager = ChatChannelManager(),
    private val chatService: ChatService =
        ChatService(chatChannelManager, playerPersister::save, sessionRegistry::all),
    private val dropConfig: DropConfig =
        DropConfig(
            BlockRegistryLoader(Path.of("resources/blocks"), Path.of("data/resources/blocks"))),
    private val worldItems: WorldItemManager =
        WorldItemManager(
            dropConfig,
            broadcast = sessionRegistry::broadcast,
            savePlayer = playerPersister::save,
            i18n = i18n,
        ),
    private val weatherConfig: WeatherConfig = WeatherConfig(),
    private val weatherManager: WeatherManager = WeatherManager(weatherConfig),
    private val configRegistry: ConfigRegistry = ConfigRegistry.buildConfigRegistry(weatherConfig),
    private val liquidManager: LiquidManager = LiquidManager(world),
    private val vegetationConfig: VegetationConfig =
        VegetationConfig(Path.of("data/config/vegetation.yaml")),
    private val vegetationManager: VegetationManager =
        VegetationManager(
            world,
            vegetationConfig,
            savePath =
                persistence?.worldDir?.resolve("vegetation_state.yaml")
                    ?: Path.of("data/world/default_world/vegetation_state.yaml"),
        ),
    private val recipeRegistryLoader: RecipeRegistryLoader =
        RecipeRegistryLoader(Path.of("data/config/recipes.yaml")),
    private val armorRegistryLoader: ArmorRegistryLoader =
        ArmorRegistryLoader(
            armorsPath = Path.of("resources/armors"),
            dataArmorsPath = Path.of("data/resources/armors"),
        ),
    private val npcConfigLoader: NpcConfigLoader = NpcConfigLoader(Path.of("data/config/npc.yaml")),
    private val npcRegistryLoader: NpcRegistryLoader =
        NpcRegistryLoader(
            resourcesEntityPath = Path.of("resources/entities"),
            dataEntityPath = Path.of("data/resources/entities"),
        ),
    private val npcManager: NpcManager =
        NpcManager(broadcast = sessionRegistry::broadcast, getSessions = sessionRegistry::all),
    private val npcSpawner: NpcSpawner = NpcSpawner(),
    private val combatConfig: CombatConfigData = CombatConfig().data,
    val attackRegistry: Map<String, AttackDefinition> = AttackConfig().data.attacks,
    val spellRegistry: Map<String, SpellDefinition> = SpellConfig().data.spells,
    private val classesData: ClassesConfigData = ClassesConfig().data,
    private val combatProcessor: CombatProcessor =
        CombatProcessor(
            config = combatConfig,
            attackRegistry = attackRegistry,
            armorRegistry = emptyMap(),
            classRegistry = classesData.classes,
            npcManager = npcManager,
            getSessions = sessionRegistry::all,
            broadcastCombatLog = { msg ->
                val chatMsg =
                    ServerMessage.ChatMessage(channel = "combat", sender = "", message = msg)
                sessionRegistry
                    .all()
                    .filter { it.state.subscribedChannels.hasChannel("combat") }
                    .forEach { it.send(chatMsg) }
            },
            subscribeToChannel = { session, channel -> chatService.subscribe(session, channel) },
            i18n = i18n,
            savePlayer = playerPersister::save,
        ),
    private val statusEffectProcessor: StatusEffectProcessor =
        StatusEffectProcessor(
            armorRegistry = emptyMap(),
            world = world,
            broadcastHealthUpdate = { id, isNpc, hp, maxHp ->
                sessionRegistry.all().forEach {
                    it.send(ServerMessage.HealthUpdate(id, isNpc, hp, maxHp))
                }
                if (!isNpc) {
                    sessionRegistry
                        .all()
                        .find { it.id == id }
                        ?.let { s ->
                            val charData = s.characterData
                            if (charData != null) {
                                val derived = DerivedStatsCalculator.compute(charData, emptyList())
                                s.send(
                                    combatProcessor.makeStatusUpdate(
                                        charData,
                                        derived,
                                        s.state.stance,
                                        s.combatState.attackCooldownUntilMs))
                            }
                        }
                }
            },
            broadcastCombatLog = { msg ->
                val chatMsg =
                    ServerMessage.ChatMessage(channel = "combat", sender = "", message = msg)
                sessionRegistry
                    .all()
                    .filter { it.state.subscribedChannels.hasChannel("combat") }
                    .forEach { it.send(chatMsg) }
            },
            subscribeToChannel = { session, channel -> chatService.subscribe(session, channel) },
            onPlayerDowned = { session -> combatProcessor.handlePlayerDowned(session) },
        ),
    private val regenProcessor: RegenProcessor =
        RegenProcessor(
            config = ClassesConfig().data,
            maxRage = combatConfig.maxRage,
            armorRegistry = emptyMap(),
            combatProcessor = combatProcessor,
        ),
    private val spellProcessor: SpellProcessor =
        SpellProcessor(
            spellRegistry = spellRegistry,
            classRegistry = classesData.classes,
            armorRegistry = emptyMap(),
            combatConfig = combatConfig,
            combatProcessor = combatProcessor,
        ),
    private val tradeConfigLoader: TradeConfigLoader =
        TradeConfigLoader(Path.of("data/config/trade.yaml")),
    private val tradeManager: TradeManager =
        TradeManager(
            getSessions = sessionRegistry::all,
            i18n = i18n,
            savePlayer = playerPersister::save,
            maxDistance = tradeConfigLoader.load().maxDistance,
        ),
    private val blockBreaker: BlockBreaker =
        BlockBreaker(world, sessionRegistry::broadcast, worldItems, liquidManager),
    private val blockPlacer: BlockPlacer =
        BlockPlacer(
            world,
            sessionRegistry::broadcast,
            playerPersister::save,
            vegetationManager,
            attackRegistry,
        ),
    private val movementProcessor: MovementProcessor = MovementProcessor(world),
    private val chunkStreamer: ChunkStreamer = ChunkStreamer(world),
    val terrainCache: TerrainCache = TerrainCache(),
    val networkStats: NetworkStats = NetworkStats(),
    private val commandContextFactory: ((CommandContextClosures) -> CommandContext)? = null,
    private val experienceProcessor: ExperienceProcessor =
        ExperienceProcessor(ExperienceConfig().data, sessionRegistry::all, playerPersister::save),
) {
    val classRegistry: Map<String, org.micoli.micraft.game.classes.ClassDefinitionEntry>
        get() = classesData.classes

    private val macroExecutor = MacroExecutor()

    private var saveTickCounter = 0
    private var timeBroadcastCounter = 0

    private var worldMeta: WorldMetadata? = persistence?.loadMetadata()
    private var gameTicks: Long = worldMeta?.gameTicks ?: 18_000L

    private val commands: Map<String, CommandHandler> = discoverCommandHandlers()

    private var armorRegistry: Map<String, ArmorDefinition> = emptyMap()
    private var npcSpawnTickCounter = 0
    private var targetDistanceTickCounter = 0

    private val commandContextClosures =
        CommandContextClosures(
            broadcast = sessionRegistry::broadcast,
            sessions = sessionRegistry::all,
            kickSession = { playerName ->
                sessionRegistry
                    .all()
                    .find { it.state.name == playerName }
                    ?.socket
                    ?.close(CloseReason(CloseReason.Codes.NORMAL, "Kicked by server"))
            },
            reloadConfig = ::reload,
            commands = { commands.values },
            savePlayer = ::savePlayer,
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
            reloadBlocks =
                if (reloadRegistries != null) {
                    {
                        reloadRegistries.invoke()
                        val sync = buildRegistrySync()
                        for (s in sessionRegistry.all()) s.send(sync)
                    }
                } else null,
            reloadNpcs = { npcManager.reloadDefinitions(npcRegistryLoader.reload()) },
            reloadRbac = reloadRbac,
            armorRegistry = { armorRegistry },
        )

    private fun buildDefaultCommandContext(closures: CommandContextClosures): CommandContext =
        CommandContext(
            world = world,
            persistence = persistence,
            i18n = i18n,
            broadcast = closures.broadcast,
            sessions = closures.sessions,
            kickSession = closures.kickSession,
            reloadConfig = closures.reloadConfig,
            commands = closures.commands,
            savePlayer = closures.savePlayer,
            worldItems = worldItems,
            npcManager = npcManager,
            getGameTime = closures.getGameTime,
            setGameTime = closures.setGameTime,
            refetchChunks = closures.refetchChunks,
            flushWorld = closures.flushWorld,
            chatService = chatService,
            chatChannelManager = chatChannelManager,
            weatherManager = weatherManager,
            authProvider = authProvider,
            groupsConfig = groupsConfig,
            liquidManager = liquidManager,
            configRegistry = configRegistry,
            reloadBlocks = closures.reloadBlocks,
            reloadNpcs = closures.reloadNpcs,
            reloadRbac = closures.reloadRbac,
            armorRegistry = closures.armorRegistry,
            tradeManager = tradeManager,
            clearTokenAccumulator = regenProcessor::clearTokenAccumulator,
            sendStatusUpdate = sendStatusUpdate@{ session ->
                    val charData = session.characterData ?: return@sendStatusUpdate
                    val armors = session.state.armors.mapNotNull { armorRegistry[it]?.statBonus }
                    val derived = DerivedStatsCalculator.compute(charData, armors)
                    session.send(
                        combatProcessor.makeStatusUpdate(
                            charData,
                            derived,
                            session.state.stance,
                            session.combatState.attackCooldownUntilMs,
                            session.combatState.attackCooldownsUntilMs,
                        ))
                },
        )

    private val commandContext =
        (commandContextFactory ?: ::buildDefaultCommandContext).invoke(commandContextClosures)
    private val intentCollector =
        IntentCollector(
            blockBreaker,
            blockPlacer,
            ::handleCommand,
            onChatSend = { session, channel, text ->
                chatService.routeMessage(session, channel, text)
            },
            combatProcessor = combatProcessor,
            spellProcessor = spellProcessor,
        )

    @Volatile private var appScope: Application? = null

    fun getPlayerStates(): List<PlayerState> = sessionRegistry.all().map { it.state }

    fun getNpcStates(): List<NpcState> = npcManager.getAll().map { it.state }

    fun getGameTicks(): Long = gameTicks

    fun getWeatherZones() = weatherManager.getZones()

    fun getWorldItemCount(): Int = worldItems.itemCount()

    fun getChunkGenerator() = world.generator

    fun getLoadedChunkCount(): Int = world.loadedChunkCount()

    fun getActiveLiquidCount(): Int = liquidManager.activeLiquidCount()

    fun getLiquidPendingTickCount(): Int = liquidManager.pendingTickCount()

    fun getActiveVegetationCount(): Int = vegetationManager.activeBlockCount()

    private fun flushWorld() {
        world.flushDirty()
        launchTerrainRebuild()
    }

    private fun launchTerrainRebuild() {
        val scope = appScope ?: return
        val chunks = world.discoveredChunks().mapNotNull { world.getChunkIfDiscovered(it) }
        scope.launch(Dispatchers.IO) {
            terrainCache.rebuild(chunks)
            persistence?.let { terrainCache.save(it.worldDir.resolve("terrain_cache.yaml")) }
        }
    }

    private fun rebuildTerrainSync() {
        val chunks = world.discoveredChunks().mapNotNull { world.getChunkIfDiscovered(it) }
        terrainCache.rebuild(chunks)
        persistence?.let { terrainCache.save(it.worldDir.resolve("terrain_cache.yaml")) }
    }

    private fun buildPreferencesSync(session: PlayerSession): ServerMessage.PreferencesSync {
        val knownChannels = chatChannelManager.listKnownChannels()
        val commandList =
            commands.values
                .filter { cmd ->
                    val p = cmd.permission
                    p == null || "*" in session.permissions || p in session.permissions
                }
                .map {
                    CommandInfo(it.id.toString(), it.command, it.description, it.autocompleteArgs)
                }
        val keybindings =
            persistence?.loadPlayerKeyBindings(session.state.name) ?: defaultKeyBindings()
        val customCommands = persistence?.loadPlayerCustomCommands(session.state.name) ?: emptyMap()
        val macros = persistence?.loadPlayerMacros(session.state.name) ?: emptyMap()
        return ServerMessage.PreferencesSync(
            subscribedChannels = session.state.subscribedChannels,
            knownChannels = knownChannels,
            disabledCommands = session.state.disabledCommands,
            shadersEnabled = session.state.shadersEnabled,
            commands = commandList,
            keybindings = keybindings,
            customCommands = customCommands,
            animatedFavicon = session.state.animatedFavicon,
            chunkDebugVisible = session.state.chunkDebugVisible,
            macros = macros,
            fieldOfView = session.state.fieldOfView,
        )
    }

    private suspend fun handlePreferencesUpdate(
        session: PlayerSession,
        msg: ClientMessage.PreferencesUpdate
    ) {
        val knownChannels = chatChannelManager.listKnownChannels()
        val newSubscribed =
            (msg.subscribedChannels.filter { it.name in knownChannels } +
                    ChatChannelManager.PROTECTED.map { ChannelSubscription(it) })
                .distinctBy { it.name }
        val shadersChanged = session.state.shadersEnabled != msg.shadersEnabled
        session.state =
            session.state.copy(
                subscribedChannels = newSubscribed,
                disabledCommands = msg.disabledCommands,
                shadersEnabled = msg.shadersEnabled,
                animatedFavicon = msg.animatedFavicon,
                chunkDebugVisible = msg.chunkDebugVisible,
                fieldOfView = msg.fieldOfView,
            )
        if (msg.keybindings.isNotEmpty()) {
            persistence?.savePlayerKeyBindings(session.state.name, msg.keybindings)
        }
        persistence?.savePlayerCustomCommands(session.state.name, msg.customCommands)
        if (msg.macros.isNotEmpty()) {
            persistence?.savePlayerMacros(session.state.name, msg.macros)
        }
        savePlayer(session)
        session.send(buildPreferencesSync(session))
        session.send(ServerMessage.ChannelsSync(newSubscribed, knownChannels))
        if (shadersChanged) session.send(ServerMessage.ShadersUpdate(msg.shadersEnabled))
    }

    private fun buildMacroContext(session: PlayerSession): MacroContext {
        val state = session.state
        return MacroContext(
            posX = state.pos.x,
            posY = state.pos.y,
            posZ = state.pos.z,
            biome = state.biome,
            yaw = state.orientation.yaw,
            pitch = state.orientation.pitch,
            currentHp = session.characterData?.currentHp ?: 0,
            currentMana = session.characterData?.currentMana ?: 0,
            effects =
                session.combatState.activeEffects.map { it.effect::class.simpleName ?: "Unknown" },
        )
    }

    private suspend fun handleRunMacro(session: PlayerSession, msg: ClientMessage.RunMacro) {
        val macros = persistence?.loadPlayerMacros(session.state.name) ?: emptyMap()
        val code = macros[msg.name] ?: return
        val pendingCommands = mutableListOf<String>()
        runCatching {
                macroExecutor.execute(
                    script = code,
                    context = buildMacroContext(session),
                    onSend = { cmd -> pendingCommands.add(cmd) },
                    onAction = {},
                )
            }
            .onFailure {
                log.warn(
                    "macro '{}' error for player {}: {}", msg.name, session.state.name, it.message)
                session.send(
                    ServerMessage.Notification(
                        i18n.t(
                            session.state.language,
                            "macros:server:error",
                            msg.name,
                            it.message ?: "")))
                return
            }
        for (cmd in pendingCommands) {
            runCatching { handleCommand(session, cmd) }
                .onFailure { e ->
                    log.warn(
                        "macro '{}' command error '{}' for {}: {}",
                        msg.name,
                        cmd,
                        session.state.name,
                        e.message)
                    session.send(
                        ServerMessage.Notification(
                            i18n.t(
                                session.state.language,
                                "macros:server:error",
                                msg.name,
                                e.message ?: "")))
                }
        }
    }

    private suspend fun handleRunMacroContent(
        session: PlayerSession,
        msg: ClientMessage.RunMacroContent,
    ) {
        val pendingCommands = mutableListOf<String>()
        runCatching {
                macroExecutor.execute(
                    script = msg.script,
                    context = buildMacroContext(session),
                    onSend = { cmd -> pendingCommands.add(cmd) },
                    onAction = {},
                )
            }
            .onFailure {
                log.warn("macro content error for player {}: {}", session.state.name, it.message)
                session.send(
                    ServerMessage.Notification(
                        i18n.t(
                            session.state.language,
                            "macros:server:error",
                            "editor",
                            it.message ?: "")))
                return
            }
        for (cmd in pendingCommands) {
            runCatching { handleCommand(session, cmd) }
                .onFailure { e ->
                    log.warn(
                        "macro content command error '{}' for {}: {}",
                        cmd,
                        session.state.name,
                        e.message)
                }
        }
    }

    private fun buildRegistrySync(): ServerMessage.RegistrySync {
        val blocks =
            BlockRegistry.orderedList().mapIndexed { i, def ->
                val name = BlockRegistry.all()[i].id
                BlockInfo(
                    name = name,
                    hardness = def.hardness,
                    solid = def.solid,
                    transparent = def.transparent,
                    minimapColor = def.minimapColor,
                    modelElement = def.modelElement,
                    liquid = def.liquid,
                    viscosity = def.viscosity,
                )
            }
        val items =
            ItemRegistry.keys().associate { type ->
                val def = ItemRegistry.get(type)
                type.id to ItemInfo(buildable = def.buildable, placesBlock = def.placesBlock?.id)
            }
        val npcs = npcManager.getDefinitions().map { (key, def) -> key to def.bbmodelFile }.toMap()
        val npcDefinitions =
            npcManager
                .getDefinitions()
                .map { (key, def) ->
                    key to
                        NpcCodexInfo(
                            bbmodelFile = def.bbmodelFile,
                            behaviorKey = def.behaviorKey,
                            width = def.width,
                            height = def.height,
                            wanderSpeed = def.wanderSpeed,
                            autoSpawn = def.spawn.autoSpawn,
                        )
                }
                .toMap()
        val npcWalkBones =
            npcManager
                .getDefinitions()
                .filter { it.value.walkBoneAliases.isNotEmpty() }
                .mapValues { it.value.walkBoneAliases }
        return ServerMessage.RegistrySync(blocks, items, npcs, npcDefinitions, npcWalkBones)
    }

    private val reloadCoordinator =
        ReloadCoordinator(
            dropConfig = dropConfig,
            world = world,
            reloadBiomes = reloadBiomes,
            reloadRegistries = reloadRegistries,
            reloadGameConfig = reloadGameConfig,
            sessionRegistry = sessionRegistry,
            buildRegistrySync = ::buildRegistrySync,
            npcConfigLoader = npcConfigLoader,
            npcRegistryLoader = npcRegistryLoader,
            npcManager = npcManager,
            i18n = i18n,
            weatherManager = weatherManager,
            vegetationManager = vegetationManager,
        )

    private suspend fun reload(lang: String): String = reloadCoordinator.reload(lang)

    fun start(app: Application) {
        appScope = app
        log.info("GameLoop starting (tick=${TICK_MS}ms, gravity=$GRAVITY)")
        validatePluginSystemIds(commands, discoverPlugins())
        RecipeRegistry.load(recipeRegistryLoader.load())
        armorRegistry = armorRegistryLoader.load()
        npcConfigLoader.load()
        npcManager.loadDefinitions(npcRegistryLoader.load())
        val npcSavePath =
            persistence?.worldDir?.resolve("npcs.yaml") ?: Path.of("data/config/spawns.json")
        npcManager.load(npcSavePath)
        vegetationManager.load()
        persistence?.let {
            terrainCache.prewarm(
                chunksDir = it.worldDir.resolve("chunks"),
                cacheFile = it.worldDir.resolve("terrain_cache.yaml"),
            )
        }
        app.launch {
            val npcSavePath =
                persistence?.worldDir?.resolve("npcs.yaml") ?: Path.of("data/config/spawns.json")
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
        runBlocking {
            val restartMsg = ServerMessage.Notification("Server restarting…")
            sessionRegistry.all().forEach { session ->
                runCatching { session.send(restartMsg) }
                runCatching {
                    session.socket.close(
                        CloseReason(CloseReason.Codes.SERVICE_RESTART, "restarting"))
                }
            }
        }
        world.flushDirty()
        rebuildTerrainSync()
        worldMeta?.let { persistence?.saveMetadata(it.copy(gameTicks = gameTicks)) }
        sessionRegistry.all().forEach { session -> savePlayer(session) }
        val npcSavePath =
            persistence?.worldDir?.resolve("npcs.yaml") ?: Path.of("data/config/spawns.json")
        npcManager.save(npcSavePath)
        vegetationManager.save()
        log.info("World saved on shutdown")
    }

    private fun savePlayer(session: PlayerSession) = playerPersister.save(session)

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
            sessionRegistry.all().forEach { it.send(timeMsg) }
        }

        sessionRegistry.all().forEach { session ->
            val input = intentCollector.collect(session)
            blockBreaker.tick(session)
            val newState = movementProcessor.process(session, input)
            if (newState != session.state) {
                session.state = newState
                val update = ServerMessage.PlayerUpdate(newState)
                sessionRegistry.all().forEach { it.send(update) }
            }
            if (session.chunkMode == "websocket") {
                chunkStreamer.checkAndRequest(session)
                chunkStreamer.deliverReady(session)
            }
        }
        worldItems.tickCollection(sessionRegistry.all())
        npcManager.tick(world)
        npcManager.tickAggro(sessionRegistry.all(), combatProcessor)
        statusEffectProcessor.tick(sessionRegistry.all())
        regenProcessor.tick(sessionRegistry.all())
        weatherManager.tick(world) { msg -> sessionRegistry.all().forEach { it.send(msg) } }
        liquidManager.tick { msg -> sessionRegistry.all().forEach { it.send(msg) } }
        vegetationManager.tick { msg -> sessionRegistry.all().forEach { it.send(msg) } }
        npcSpawnTickCounter++
        if (npcSpawnTickCounter >= NpcConstants.SPAWN_CHECK_INTERVAL_TICKS) {
            npcSpawnTickCounter = 0
            npcSpawner.trySpawn(
                world, npcManager, npcManager.getDefinitions(), world.discoveredChunks())
        }
        targetDistanceTickCounter++
        if (targetDistanceTickCounter >= TARGET_DISTANCE_REFRESH_TICKS) {
            targetDistanceTickCounter = 0
            sessionRegistry.all().forEach { session ->
                if (session.combatState.targetId != null) {
                    session.send(combatProcessor.buildTargetUpdate(session))
                }
            }
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
            val perm = handler.permission
            if (perm != null && "*" !in session.permissions && perm !in session.permissions) {
                session.send(
                    ServerMessage.Notification(
                        i18n.t(session.state.language, "rbac:server:no_permission")))
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
        val session = sessionRegistry.all().find { it.state.name == playerName }
        return handler.completeArg(argIndex, partial, session, commandContext)
    }

    suspend fun onConnect(socket: DefaultWebSocketSession) {
        val id = UUID.randomUUID().toString()

        val connectMsg =
            runCatching {
                    val firstFrame = socket.incoming.receive()
                    if (firstFrame is Frame.Binary) {
                        val msg = ClientMessageCodec.decode(firstFrame.readBytes())
                        if (msg is ClientMessage.Connect) msg else null
                    } else null
                }
                .getOrNull()
        val authResult =
            if (tokenStore != null) {
                val token = connectMsg?.token ?: ""
                val result = tokenStore.validate(token)
                if (result == null) {
                    socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid token"))
                    return
                }
                result
            } else null

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
                layouts = saved?.layouts ?: listOf(defaultLayout()),
                activeLayout = saved?.activeLayout ?: "default",
                subscribedChannels =
                    saved?.subscribedChannels
                        ?: listOf(
                            ChannelSubscription("world"),
                            ChannelSubscription("system"),
                            ChannelSubscription("game")),
                disabledCommands = saved?.disabledCommands ?: emptySet(),
                viewMode = saved?.viewMode ?: "FIRST_PERSON",
                skin = saved?.skin ?: "player",
                armors = saved?.armors ?: emptyList(),
                animatedFavicon = saved?.animatedFavicon ?: true,
                chunkDebugVisible = saved?.chunkDebugVisible ?: false,
                fieldOfView = saved?.fieldOfView ?: 70,
                knownRecipes = saved?.knownRecipes ?: emptySet(),
                rpgOptOut =
                    if (saved?.characterData != null) false else (saved?.rpgOptOut ?: false),
            )
        val sessionPermissions = authResult?.permissions ?: setOf("*")
        val session =
            PlayerSession(
                id,
                userName,
                socket,
                state,
                networkStats = networkStats,
                permissions = sessionPermissions,
                chunkMode = chunkSection.transport)
        saved?.inventory?.forEach { (type, count) -> session.inventory[type] = count }
        saved?.shortcutBar?.forEachIndexed { i, item ->
            if (i in 0..9) session.shortcutBar[i] = item
        }
        session.characterData = saved?.characterData
        log.info(
            "player connected: {} name={} user={} (total={})",
            id.take(8),
            playerName,
            userName,
            sessionRegistry.size + 1)

        session.send(
            ServerMessage.Welcome(
                id,
                playerName,
                spawn,
                language,
                shadersEnabled,
                session.state.layouts,
                session.state.activeLayout,
                session.state.viewMode,
                RECONCILE_TOLERANCE_XZ,
                RECONCILE_TOLERANCE_Y,
                chunkSection.transport))
        session.send(buildRegistrySync())
        session.send(
            ServerMessage.RecipeSync(
                recipes = RecipeRegistry.all(),
                knownRecipes = session.knownRecipes.toSet(),
            ))
        session.send(buildPreferencesSync(session))
        chatService.onPlayerConnect(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(ServerMessage.ShortcutBarUpdate(session.shortcutBar.toSlotMap()))
        session.send(ServerMessage.TimeUpdate(gameTicks))
        val charData = session.characterData
        if (charData != null) {
            val derived =
                DerivedStatsCalculator.compute(
                    charData, session.state.armors.mapNotNull { armorRegistry[it]?.statBonus })
            session.send(
                ServerMessage.CharacterSync(
                    charData,
                    derived,
                    DerivedStatsCalculator.effectiveBaseStats(
                        charData,
                        session.state.armors.mapNotNull { armorRegistry[it]?.statBonus })))
            session.send(
                combatProcessor.makeStatusUpdate(
                    charData,
                    derived,
                    session.state.stance,
                    session.combatState.attackCooldownUntilMs))
            experienceProcessor.sendXpState(session)
        } else if (!session.state.rpgOptOut) {
            session.send(ServerMessage.CharacterCreationRequired)
        }

        val spawnCp =
            ChunkPos(
                Math.floorDiv(spawn.x.toInt(), WorldConstants.CHUNK_SIZE),
                Math.floorDiv(spawn.z.toInt(), WorldConstants.CHUNK_SIZE),
            )
        session.lastChunkPos = spawnCp
        chunkStreamer.sendCenterChunkNow(session, spawnCp)
        chunkStreamer.requestAround(session, spawnCp.cx, spawnCp.cz)
        log.info("chunk requests queued for {}", id.take(8))

        sessionRegistry
            .all()
            .filter { it.id != id }
            .forEach { other ->
                session.send(ServerMessage.PlayerUpdate(other.state))
                other.send(ServerMessage.PlayerUpdate(state))
            }
        npcManager.sendAllTo(session)
        sessionRegistry[id] = session

        try {
            socket.incoming.consumeEach { frame ->
                if (frame is Frame.Binary) {
                    val frameBytes = frame.readBytes()
                    networkStats.bytesIn.addAndGet(frameBytes.size.toLong())
                    runCatching { ClientMessageCodec.decode(frameBytes) }
                        .onFailure { log.warn("bad frame from {}: {}", id.take(8), it.message) }
                        .getOrNull()
                        ?.let { msg ->
                            runCatching {
                                    when (msg) {
                                        is ClientMessage.Disconnect -> return@consumeEach
                                        is ClientMessage.ChunkUnload -> {
                                            msg.positions.forEach {
                                                session.loadedChunks.remove(it)
                                            }
                                            log.debug(
                                                "{} chunks unloaded by {}",
                                                msg.positions.size,
                                                session.id.take(8))
                                        }
                                        is ClientMessage.LayoutUpdate ->
                                            handleLayoutUpdate(session, msg)
                                        is ClientMessage.PreferencesUpdate ->
                                            handlePreferencesUpdate(session, msg)
                                        is ClientMessage.ViewModeUpdate -> {
                                            session.state =
                                                session.state.copy(viewMode = msg.viewMode)
                                            savePlayer(session)
                                        }
                                        is ClientMessage.NpcInteract ->
                                            npcManager.handleInteract(session, msg.npcId)
                                        is ClientMessage.RunMacro -> handleRunMacro(session, msg)
                                        is ClientMessage.RunMacroContent ->
                                            handleRunMacroContent(session, msg)
                                        else -> session.intents.trySend(msg)
                                    }
                                }
                                .onFailure { e ->
                                    log.error(
                                        "unhandled exception for msg {} player {}: {}",
                                        msg::class.simpleName,
                                        id.take(8),
                                        e.message,
                                        e)
                                }
                        }
                }
            }
        } finally {
            sessionRegistry.remove(id)
            chunkStreamer.cleanupSession(id)
            npcManager.clearPlayer(id)
            tradeManager.onPlayerDisconnect(id)
            savePlayer(session)
            log.info(
                "player disconnected: {} name={} (total={})",
                id.take(8),
                session.state.name,
                sessionRegistry.size)
            val left = ServerMessage.PlayerLeft(id)
            sessionRegistry.all().forEach { it.send(left) }
        }
    }

    suspend fun onChunkConnect(socket: DefaultWebSocketSession) {
        val playerId =
            runCatching {
                    val frame = socket.incoming.receive()
                    if (frame is Frame.Text) frame.readText().trim() else null
                }
                .getOrNull() ?: return
        val session = sessionRegistry[playerId] ?: return
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
