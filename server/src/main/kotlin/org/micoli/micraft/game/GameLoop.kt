package org.micoli.micraft.game

import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import io.ktor.server.application.*
import io.ktor.websocket.*
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.SERVER_BUILD_TIMESTAMP
import org.micoli.micraft.auth.AuthProvider
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.auth.NoAuthAccountStore
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Plugin
import org.micoli.micraft.command.commands.resolveSkin
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.di.CommandContextClosures
import org.micoli.micraft.di.PlayerPersister
import org.micoli.micraft.di.ReloadCoordinator
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.ArmorRegistryLoader
import org.micoli.micraft.game.auction.AuctionConfigLoader
import org.micoli.micraft.game.auction.AuctionManager
import org.micoli.micraft.game.auction.AuctionPersistence
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.classes.ClassesConfig
import org.micoli.micraft.game.classes.ClassesConfigData
import org.micoli.micraft.game.combat.CombatConfig
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.RegenProcessor
import org.micoli.micraft.game.combat.SkillsConfig
import org.micoli.micraft.game.combat.SpellDefinition
import org.micoli.micraft.game.combat.SpellProcessor
import org.micoli.micraft.game.combat.StatusEffectProcessor
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.equipment.ToolCategoryDefinition
import org.micoli.micraft.game.equipment.ToolCategoryRegistryLoader
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.equipment.ToolRegistryLoader
import org.micoli.micraft.game.equipment.WeaponCategoryDefinition
import org.micoli.micraft.game.equipment.WeaponCategoryRegistryLoader
import org.micoli.micraft.game.equipment.WeaponDefinition
import org.micoli.micraft.game.equipment.WeaponRegistryLoader
import org.micoli.micraft.game.keybinding.defaultKeyBindings
import org.micoli.micraft.game.macro.MacroContext
import org.micoli.micraft.game.macro.MacroExecutor
import org.micoli.micraft.game.mail.MailManager
import org.micoli.micraft.game.mail.MailPersistence
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcConstants
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.npc.NpcSpawner
import org.micoli.micraft.game.npc.NpcSubsystemFactory
import org.micoli.micraft.game.npc.NpcSubsystemHooks
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileTickPipeline
import org.micoli.micraft.game.placeable.siege.SiegeWeaponManager
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestRegistryLoader
import org.micoli.micraft.game.recipe.RecipeRegistry
import org.micoli.micraft.game.recipe.RecipeRegistryLoader
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.rpg.ExperienceConfig
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.game.rpg.equipmentBonuses
import org.micoli.micraft.game.session.NetworkStats
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.hasPermission
import org.micoli.micraft.game.session.toPageMap
import org.micoli.micraft.game.tick.ChunkStreamer
import org.micoli.micraft.game.tick.IntentCollector
import org.micoli.micraft.game.tick.MovementProcessor
import org.micoli.micraft.game.tick.TickProfiler
import org.micoli.micraft.game.trade.TradeConfigLoader
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.game.vehicle.VehicleTickPipeline
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.EquipmentCategory
import org.micoli.micraft.game.world.GameWorld
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.block.BlockInteractor
import org.micoli.micraft.game.world.block.BlockPlacer
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.game.world.claim.ClaimConfigLoader
import org.micoli.micraft.game.world.claim.ClaimManager
import org.micoli.micraft.game.world.claim.ClaimRegistry
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.instance.toProto
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.rail.RailNetworkRegistry
import org.micoli.micraft.game.world.sanitizePlayerName
import org.micoli.micraft.game.world.scene.ScenePlacer
import org.micoli.micraft.game.world.scene.SceneRegistry
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.game.world.weather.WeatherConfig
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.placeable.PlaceableRegistry
import org.micoli.micraft.placeable.siege.SiegeProjectileRegistry
import org.micoli.micraft.placeable.siege.SiegeWeaponRegistry
import org.micoli.micraft.player.ChannelSubscription
import org.micoli.micraft.player.EditMode
import org.micoli.micraft.player.Hand
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.hasChannel
import org.micoli.micraft.plugin.PluginLoader
import org.micoli.micraft.plugin.TickContext
import org.micoli.micraft.plugin.TickHandler
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.BlockInfo
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ClientMessageCodec
import org.micoli.micraft.protocol.CommandInfo
import org.micoli.micraft.protocol.ItemInfo
import org.micoli.micraft.protocol.NpcCodexInfo
import org.micoli.micraft.protocol.PlaceableCodexInfo
import org.micoli.micraft.protocol.PlainColorInfo
import org.micoli.micraft.protocol.RailInfo
import org.micoli.micraft.protocol.SceneSummaryProto
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.SiegeProjectileCodexInfo
import org.micoli.micraft.protocol.SiegeWeaponCodexInfo
import org.micoli.micraft.protocol.VehicleCodexInfo
import org.micoli.micraft.ui.defaultLayout
import org.micoli.micraft.ui.validateLayouts
import org.micoli.micraft.vehicle.VehicleRegistry
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GameLoop")

private class ClasspathDiscovery(
    val commandHandlers: Map<String, CommandHandler>,
    val plugins: List<Plugin>,
)

/**
 * Scanning the classpath costs ~hundreds of ms, so both discoveries share a single scan performed
 * once per JVM. Command handlers and plugins are stateless, and [GameLoop] copies the handler map
 * before registering plugin commands into it, so sharing the instances is safe.
 */
private val classpathDiscovery: ClasspathDiscovery by lazy {
    ClassGraph().enableClassInfo().acceptPackages("org.micoli.micraft").scan().use { result ->
        ClasspathDiscovery(
            commandHandlers =
                result.instantiateImplementations<CommandHandler>("command handler").associateBy {
                    it.command
                },
            plugins = result.instantiateImplementations<Plugin>("plugin"),
        )
    }
}

private inline fun <reified T : Any> ScanResult.instantiateImplementations(kind: String): List<T> =
    getClassesImplementing(T::class.java)
        .filter { !it.isAbstract && !it.isInterface }
        .mapNotNull { info ->
            runCatching {
                    @Suppress("UNCHECKED_CAST")
                    (info.loadClass() as Class<T>).getDeclaredConstructor().newInstance()
                }
                .onFailure { e -> log.warn("Failed to load {} {}: {}", kind, info.name, e.message) }
                .getOrNull()
        }

fun discoverCommandHandlers(): Map<String, CommandHandler> = classpathDiscovery.commandHandlers

fun discoverPlugins(): List<Plugin> = classpathDiscovery.plugins

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
    private val reloadFactionsConfig: (() -> org.micoli.micraft.game.FactionsSection)? = null,
    val i18n: I18nConfig = I18nConfig.fromClasspath(pluginsRoot = Path.of("plugins")),
    private val tokenStore: TokenStore? = null,
    private val authProvider: AuthProvider? = null,
    private val groupsConfig: GroupsConfig? = null,
    private val reloadRbac: (() -> Unit)? = null,
    private val noAuthAccountStore: NoAuthAccountStore? = null,
    private val chunkSection: ChunkSection = ChunkSection(),
    private val sessionRegistry: SessionRegistry = SessionRegistry(),
    private val playerPersister: PlayerPersister = PlayerPersister(persistence),
    private val chatChannelManager: ChatChannelManager = ChatChannelManager(),
    private val chatService: ChatService =
        ChatService(chatChannelManager, playerPersister::save, sessionRegistry::all),
    private val factionsSection: org.micoli.micraft.game.FactionsSection =
        org.micoli.micraft.game.FactionsSection(),
    private val factionManager: org.micoli.micraft.game.social.FactionManager =
        org.micoli.micraft.game.social.FactionManager(
            getSessions = sessionRegistry::all,
            savePlayer = playerPersister::save,
            chatService = chatService,
            channelManager = chatChannelManager,
            i18n = i18n,
            broadcast = sessionRegistry::broadcast,
            persistence = persistence,
        ),
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
    private val weaponRegistryLoader: WeaponRegistryLoader =
        WeaponRegistryLoader(
            weaponsPath = Path.of("resources/weapons"),
            dataWeaponsPath = Path.of("data/resources/weapons"),
        ),
    private val toolRegistryLoader: ToolRegistryLoader =
        ToolRegistryLoader(
            toolsPath = Path.of("resources/tools"),
            dataToolsPath = Path.of("data/resources/tools"),
        ),
    private val weaponCategoryRegistryLoader: WeaponCategoryRegistryLoader =
        WeaponCategoryRegistryLoader(Path.of("data/config/weapons.yaml")),
    private val toolCategoryRegistryLoader: ToolCategoryRegistryLoader =
        ToolCategoryRegistryLoader(Path.of("data/config/tools.yaml")),
    private val instanceRegistry: InstanceRegistry = InstanceRegistry(persistence),
    private val claimRegistry: ClaimRegistry = ClaimRegistry(persistence),
    private var weaponRegistry: Map<String, WeaponDefinition> = weaponRegistryLoader.load(),
    private var toolRegistry: Map<String, ToolDefinition> = toolRegistryLoader.load(),
    private var weaponCategories: Map<EquipmentCategory, WeaponCategoryDefinition> =
        weaponCategoryRegistryLoader.load(),
    private var toolCategories: Map<EquipmentCategory, ToolCategoryDefinition> =
        toolCategoryRegistryLoader.load(),
    private val railNetworkRegistry: RailNetworkRegistry = RailNetworkRegistry(world),
    private val vehicleManager: VehicleManager = VehicleManager(sessionRegistry::broadcast),
    private val vehicleTickPipeline: VehicleTickPipeline = VehicleTickPipeline(vehicleManager),
    private val placeableManager: PlaceableManager = PlaceableManager(sessionRegistry::broadcast),
    private val siegeWeaponManager: SiegeWeaponManager =
        SiegeWeaponManager(sessionRegistry::broadcast),
    private val siegeProjectileManager: SiegeProjectileManager =
        SiegeProjectileManager(sessionRegistry::broadcast),
    private val siegeProjectileTickPipeline: SiegeProjectileTickPipeline =
        SiegeProjectileTickPipeline(siegeProjectileManager),
    private val blockInteractor: BlockInteractor =
        BlockInteractor(
            world,
            sessionRegistry::broadcast,
            instanceRegistry = instanceRegistry,
            claimRegistry = claimRegistry,
            railNetworkRegistry = railNetworkRegistry,
        ),
    private val sceneRegistry: SceneRegistry = SceneRegistry(persistence),
    private val npcConfigLoader: NpcConfigLoader = NpcConfigLoader(Path.of("data/config/npc.yaml")),
    private val npcRegistryLoader: NpcRegistryLoader =
        NpcRegistryLoader(
            resourcesEntityPath = Path.of("resources/entities"),
            dataEntityPath = Path.of("data/resources/entities"),
        ),
    /**
     * The single wiring of the NPC subsystem, shared with the admin world simulator.
     *
     * The default here is deliberately hook-poor — it is what a test that does not care gets. The
     * real server passes the Koin-provided factory, which carries XP, quest credit and chat
     * fan-out.
     */
    private val npcSubsystemFactory: NpcSubsystemFactory =
        NpcSubsystemFactory(
            hooks =
                NpcSubsystemHooks(
                    broadcast = sessionRegistry::broadcast,
                    broadcastWorldUpdate = sessionRegistry::broadcast,
                    getSessions = sessionRegistry::all,
                ),
            world = world,
            vegetationManager = vegetationManager,
            gameDayDurationSecondsOf = { NpcConstants.live.gameDayDurationSeconds },
        ),
    private val npcManager: NpcManager = npcSubsystemFactory.npcManager,
    private val npcSpawner: NpcSpawner = npcSubsystemFactory.npcSpawner,
    private val combatConfig: CombatConfigData = CombatConfig().data,
    val attackRegistry: Map<String, AttackDefinition> = SkillsConfig().data.attacks,
    val spellRegistry: Map<String, SpellDefinition> = SkillsConfig().data.spells,
    private val classesData: ClassesConfigData = ClassesConfig().data,
    private val combatConfigLoader: CombatConfig? = null,
    private val skillsConfigLoader: SkillsConfig? = null,
    private val classesConfigLoader: ClassesConfig? = null,
    private val experienceConfigLoader: ExperienceConfig? = null,
    private val combatProcessor: CombatProcessor =
        CombatProcessor(
            config = combatConfig,
            attackRegistry = attackRegistry,
            armorRegistry = armorRegistryLoader.load(),
            weaponRegistry = weaponRegistry,
            toolRegistry = toolRegistry,
            classRegistry = classesData.classes,
            npcManager = npcManager,
            vehicleManager = vehicleManager,
            placeableManager = placeableManager,
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
            factionManager = factionManager,
        ),
    private val statusEffectProcessor: StatusEffectProcessor =
        StatusEffectProcessor(
            armorRegistry = armorRegistryLoader.load(),
            weaponRegistry = weaponRegistry,
            toolRegistry = toolRegistry,
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
                                        s.combatState.attackCooldownUntilMs,
                                        s.combatState.attackCooldownsUntilMs,
                                        s.state.godMode))
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
            armorRegistry = armorRegistryLoader.load(),
            weaponRegistry = weaponRegistry,
            toolRegistry = toolRegistry,
            combatProcessor = combatProcessor,
        ),
    private val spellProcessor: SpellProcessor =
        SpellProcessor(
            spellRegistry = spellRegistry,
            classRegistry = classesData.classes,
            armorRegistry = armorRegistryLoader.load(),
            weaponRegistry = weaponRegistry,
            toolRegistry = toolRegistry,
            combatConfig = combatConfig,
            combatProcessor = combatProcessor,
            getSessions = sessionRegistry::all,
            getNpcs = { npcManager.getAll() },
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
    private val auctionConfigLoader: AuctionConfigLoader =
        AuctionConfigLoader(Path.of("data/config/auction.yaml")),
    private val auctionManager: AuctionManager? =
        persistence?.worldDir?.let { worldDir ->
            AuctionManager(
                getSessions = sessionRegistry::all,
                i18n = i18n,
                savePlayer = playerPersister::save,
                persistence = AuctionPersistence(worldDir),
                mailManager = null,
                config = auctionConfigLoader.load(),
            )
        },
    private val blockBreaker: BlockBreaker =
        BlockBreaker(
            world,
            sessionRegistry::broadcast,
            worldItems,
            liquidManager,
            instanceRegistry = instanceRegistry,
            claimRegistry = claimRegistry,
            railNetworkRegistry = railNetworkRegistry,
            weaponRegistry = { weaponRegistry },
            toolRegistry = { toolRegistry },
        ),
    private val blockPlacer: BlockPlacer =
        BlockPlacer(
            world,
            sessionRegistry::broadcast,
            playerPersister::save,
            vegetationManager,
            attackRegistry,
            instanceRegistry = instanceRegistry,
            claimRegistry = claimRegistry,
            railNetworkRegistry = railNetworkRegistry,
            placeableManager = placeableManager,
            siegeWeaponManager = siegeWeaponManager,
        ),
    private val claimConfigLoader: ClaimConfigLoader =
        ClaimConfigLoader(Path.of("data/config/claims.yaml")),
    private val claimManager: ClaimManager =
        ClaimManager(
            registry = claimRegistry,
            config = claimConfigLoader.load(),
            getSessions = sessionRegistry::all,
            i18n = i18n,
            savePlayer = playerPersister::save,
            persistence = persistence,
        ),
    private val movementProcessor: MovementProcessor = MovementProcessor(world),
    private val chunkStreamer: ChunkStreamer = ChunkStreamer(world),
    val terrainCache: TerrainCache = TerrainCache(),
    val networkStats: NetworkStats = NetworkStats(),
    private val commandContextFactory: ((CommandContextClosures) -> CommandContext)? = null,
    private val experienceProcessor: ExperienceProcessor =
        ExperienceProcessor(ExperienceConfig().data, sessionRegistry::all, playerPersister::save),
    private val petManager: org.micoli.micraft.game.pet.PetManager =
        org.micoli.micraft.game.pet.PetManager(
            npcManager = npcManager,
            experienceProcessor = experienceProcessor,
            getSessions = sessionRegistry::all,
            savePlayer = playerPersister::save,
            i18n = i18n,
        ),
    private val petCoordinator: org.micoli.micraft.game.pet.PetCoordinator =
        org.micoli.micraft.game.pet.PetCoordinator(
            npcManager = npcManager,
            combatConfig = combatConfig,
        ),
    private val questManager: QuestManager? = null,
    private val questRegistryLoader: QuestRegistryLoader? = null,
) {
    val classRegistry: Map<String, org.micoli.micraft.game.classes.ClassDefinitionEntry>
        get() = classesData.classes

    private fun reloadCombatSystems() {
        val newCombat = combatConfigLoader?.reload() ?: combatConfig
        val newSkills = skillsConfigLoader?.reload()
        val newClasses = classesConfigLoader?.reload() ?: classesData
        val newExperience = experienceConfigLoader?.reload()
        val freshArmor = armorRegistry
        combatProcessor.reload(
            newCombat,
            newSkills?.attacks ?: attackRegistry,
            freshArmor,
            newClasses.classes,
            weaponRegistry,
            toolRegistry)
        regenProcessor.reload(
            newClasses, newCombat.maxRage, freshArmor, weaponRegistry, toolRegistry)
        spellProcessor.reload(
            newSkills?.spells ?: spellRegistry,
            newClasses.classes,
            freshArmor,
            newCombat,
            weaponRegistry,
            toolRegistry)
        statusEffectProcessor.reload(freshArmor, weaponRegistry, toolRegistry)
        if (newExperience != null) experienceProcessor.reload(newExperience)
        if (newSkills != null) blockPlacer.reload(newSkills.attacks)
    }

    private val mailManager: MailManager? =
        persistence?.worldDir?.resolve("players")?.let { playersDir ->
            MailManager(
                persistence = MailPersistence(playersDir),
                sessionRegistry = sessionRegistry,
                i18n = i18n,
                savePlayer = playerPersister::save,
            )
        }

    private val guildRegistry: org.micoli.micraft.game.social.GuildRegistry =
        org.micoli.micraft.game.social.GuildRegistry(persistence)

    private val guildManager: org.micoli.micraft.game.social.GuildManager =
        org.micoli.micraft.game.social.GuildManager(
            registry = guildRegistry,
            getSessions = sessionRegistry::all,
            savePlayer = playerPersister::save,
            chatService = chatService,
            channelManager = chatChannelManager,
            i18n = i18n,
            returnBankItems = { playerName, items ->
                mailManager?.deliverSystemMail(
                    to = playerName,
                    subject = "Guild disbanded",
                    body = "Your guild was disbanded. The guild bank contents are attached.",
                    attachments = items,
                )
            },
        )

    private val groupManager: org.micoli.micraft.game.social.GroupManager =
        org.micoli.micraft.game.social.GroupManager(
            getSessions = sessionRegistry::all,
            chatService = chatService,
            channelManager = chatChannelManager,
            i18n = i18n,
        )

    init {
        npcManager.onPetDied = { pet -> petManager.onPetDied(pet) }
        npcManager.onNpcKilledForPets = { killed -> petManager.grantSharedXpForKill(killed) }
        experienceProcessor.onNpcLevelUp = { npc, level ->
            if (npc.ownerId != null) petManager.onPetLevelUp(npc, level)
        }
    }

    init {
        chatService.groupMembers = { id -> groupManager.memberIds(id) }
        chatService.guildMembers = { id -> guildRegistry.memberIds(id) }
        claimRegistry.factionAlly = { actorId, ownerId ->
            factionManager.sameFaction(actorId, ownerId)
        }
        factionManager.applyConfig(factionsSection)
    }

    private val macroExecutor = MacroExecutor()

    private var saveTickCounter = 0
    private var timeBroadcastCounter = 0

    /**
     * The NPC subsystem, wired by the shared factory rather than here.
     *
     * The admin world simulator builds the same object from the same factory, which is what makes a
     * simulated run describe this game. Wiring it a second time here is how the simulator ended up
     * without kill XP, without the population veto and with its own tick cadence.
     */
    private val npcSubsystem = npcSubsystemFactory.build(combatProcessor, petCoordinator)

    val gameTimeService: GameTimeService = npcSubsystem.gameTimeService

    private val animalInteractionProcessor = npcSubsystem.animals

    private val packCoordinator = npcSubsystem.packs

    /** Owns the NPC tick order; shared with the admin world simulator. */
    private val npcTickPipeline = npcSubsystem.pipeline

    @Volatile private var appScope: Application? = null

    private val gameWorld =
        GameWorld(
            id = GameWorld.DEFAULT_ID,
            world = world,
            persistence = persistence,
            sessions = sessionRegistry,
            terrainCache = terrainCache,
            npcManager = npcManager,
            vehicleManager = vehicleManager,
            placeableManager = placeableManager,
            siegeWeaponManager = siegeWeaponManager,
            vegetationManager = vegetationManager,
            gameTimeService = gameTimeService,
            appScope = { appScope },
        )

    private val commands: MutableMap<String, CommandHandler> =
        discoverCommandHandlers().toMutableMap()
    private val pluginTickHandlers: MutableList<TickHandler> = mutableListOf()

    private var armorRegistry: Map<String, ArmorDefinition> = emptyMap()
    private var targetDistanceTickCounter = 0
    private var npcLifecycleTickCounter = 0

    private val tickProfiler = TickProfiler()

    fun getTickProfile() = tickProfiler.snapshot()

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
            getGameTime = { gameWorld.gameTicks },
            setGameTime = { gameWorld.gameTicks = it },
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
            weaponRegistry = { weaponRegistry },
            toolRegistry = { toolRegistry },
            weaponCategories = { weaponCategories },
            toolCategories = { toolCategories },
            applyBuff = { session, effect, durationSec ->
                combatProcessor.applyStatusEffectTo(
                    session, effect, durationSec, System.currentTimeMillis())
            },
            groupManager = groupManager,
            guildManager = guildManager,
            guildRegistry = guildRegistry,
            factionManager = factionManager,
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
            petManager = petManager,
            vehicleManager = vehicleManager,
            placeableManager = placeableManager,
            siegeWeaponManager = siegeWeaponManager,
            scenes = sceneRegistry,
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
            weaponRegistry = closures.weaponRegistry,
            toolRegistry = closures.toolRegistry,
            weaponCategories = closures.weaponCategories,
            toolCategories = closures.toolCategories,
            tradeManager = tradeManager,
            auctionManager = auctionManager,
            claimRegistry = claimRegistry,
            claimManager = claimManager,
            groupManager = groupManager,
            guildManager = guildManager,
            guildRegistry = guildRegistry,
            factionManager = factionManager,
            questManager = questManager,
            clearAccumulators = regenProcessor::clearAccumulators,
            applyBuff = closures.applyBuff,
            sendStatusUpdate = sendStatusUpdate@{ session ->
                    val charData = session.characterData ?: return@sendStatusUpdate
                    val armors =
                        session.state.equipmentBonuses(armorRegistry, weaponRegistry, toolRegistry)
                    val effectNames =
                        session.combatState.activeEffects
                            .map { it.effect::class.simpleName ?: "" }
                            .toSet()
                    val derived = DerivedStatsCalculator.compute(charData, armors, effectNames)
                    session.send(
                        combatProcessor.makeStatusUpdate(
                            charData,
                            derived,
                            session.state.stance,
                            session.combatState.attackCooldownUntilMs,
                            session.combatState.attackCooldownsUntilMs,
                            session.state.godMode,
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
            blockInteractor = blockInteractor,
            onChatSend = { session, channel, text ->
                chatService.routeMessage(session, channel, text)
            },
            combatProcessor = combatProcessor,
            spellProcessor = spellProcessor,
            onConsumeItem = ::handleConsumeItem,
        )

    fun getPlayerStates(): List<PlayerState> = sessionRegistry.all().map { it.state }

    /** Live session for a connected player, looked up by display name — null if offline. */
    fun findSession(name: String): PlayerSession? =
        sessionRegistry.all().find {
            sanitizePlayerName(it.state.name).equals(name, ignoreCase = true)
        }

    /**
     * Persists a live session correctly — folds session-only fields (inventory, characterData,
     * shortcutBarPages, knownRecipes) into [PlayerState] before writing, unlike a bare
     * `persistence.savePlayerState` which would clobber them with a stale snapshot.
     */
    fun savePlayerSession(session: PlayerSession) = playerPersister.save(session)

    suspend fun broadcastPlayerUpdate(session: PlayerSession) =
        sessionRegistry.broadcast(ServerMessage.PlayerUpdate(session.state))

    fun getWorldState(): WorldState = world

    fun railNetworkRegistry(): RailNetworkRegistry = railNetworkRegistry

    fun instances(): InstanceRegistry = instanceRegistry

    fun claims(): ClaimRegistry = claimRegistry

    fun scenes(): SceneRegistry = sceneRegistry

    // Pushed to every connected admin whenever the zone list changes, so the minimap's unified
    // outlines (unlike AdminZoneWireframe, which only covers the zone the player is standing in)
    // stay in sync without requiring a reconnect.
    suspend fun broadcastInstanceZonesSync() {
        val msg = ServerMessage.InstanceZonesSync(instanceRegistry.all().map { it.toProto() })
        sessionRegistry.all().filter { it.hasPermission("admin") }.forEach { it.send(msg) }
    }

    // Instance zone blocks live in the same shared WorldState as everywhere else — the admin
    // block editor bypasses BlockPlacer/BlockBreaker (which reject edits inside protected zones
    // for normal players), so it must broadcast the WorldUpdate itself for players already
    // standing near the zone to see the edit without reconnecting.
    suspend fun broadcastWorldUpdate(
        changes: List<BlockChange>,
        entityAdds: List<org.micoli.micraft.protocol.BlockEntityProto> = emptyList(),
        entityRemoves: List<org.micoli.micraft.game.world.BlockPos> = emptyList(),
        entityRemovesAt: List<org.micoli.micraft.protocol.EntityRemoveAt> = emptyList(),
    ) {
        sessionRegistry.broadcast(
            ServerMessage.WorldUpdate(changes, entityAdds, entityRemoves, entityRemovesAt))
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
        for (l in playerAdminListeners) runCatching { l(json) }
    }

    private fun String.toPlayerAdminJson() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

    fun getNpcStates(): List<NpcState> = npcManager.getAll().map { it.state }

    fun getGameTicks(): Long = gameWorld.gameTicks

    fun setGameTicks(ticks: Long) {
        gameWorld.gameTicks = ticks
    }

    fun getWeatherZones() = weatherManager.getZones()

    fun getNpcInstances() = npcManager.getAll()

    fun getNpcManager() = npcManager

    fun getMailManager() = mailManager

    fun getAuctionManager() = auctionManager

    fun getWorldItemCount(): Int = worldItems.itemCount()

    fun getChunkGenerator() = world.generator

    fun getLoadedChunkCount(): Int = world.loadedChunkCount()

    fun getActiveLiquidCount(): Int = liquidManager.activeLiquidCount()

    fun getLiquidPendingTickCount(): Int = liquidManager.pendingTickCount()

    fun getActiveVegetationCount(): Int = vegetationManager.activeBlockCount()

    private fun flushWorld() = gameWorld.flush()

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
        val defaults = defaultKeyBindings()
        val keybindings = persistence?.loadPlayerKeyBindings(session.state.name) ?: defaults
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
            statisticsVisible = session.state.statisticsVisible,
            attackPanelVisible = session.state.attackPanelVisible,
            macros = macros,
            fieldOfView = session.state.fieldOfView,
            defaultKeybindings = defaults,
            dynamicFogEnabled = session.state.dynamicFogEnabled,
            autoTargetEnabled = session.state.autoTargetEnabled,
            inventorySortA = session.state.inventorySortA,
            inventorySortB = session.state.inventorySortB,
            shadowAngleDeg = session.state.shadowAngleDeg,
            overrideViewRadius = session.state.overrideViewRadius,
            overrideForwardViewRadius = session.state.overrideForwardViewRadius,
            overrideUseImpostor = session.state.overrideUseImpostor,
            overrideImpostorRadiusChunks = session.state.overrideImpostorRadiusChunks,
            overrideImpostorFovBonusChunks = session.state.overrideImpostorFovBonusChunks,
            continuousBreak = session.state.continuousBreak,
            dominantHand = session.state.dominantHand,
            disabledViewModes = session.state.disabledViewModes,
            turnSpeedHorizontal = session.state.turnSpeedHorizontal,
            turnSpeedVertical = session.state.turnSpeedVertical,
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
                statisticsVisible = msg.statisticsVisible,
                attackPanelVisible = msg.attackPanelVisible,
                fieldOfView = msg.fieldOfView,
                dynamicFogEnabled = msg.dynamicFogEnabled,
                autoTargetEnabled = msg.autoTargetEnabled,
                inventorySortA = msg.inventorySortA,
                inventorySortB = msg.inventorySortB,
                shadowAngleDeg = msg.shadowAngleDeg,
                overrideViewRadius = msg.overrideViewRadius,
                overrideForwardViewRadius = msg.overrideForwardViewRadius,
                overrideUseImpostor = msg.overrideUseImpostor,
                overrideImpostorRadiusChunks = msg.overrideImpostorRadiusChunks,
                overrideImpostorFovBonusChunks = msg.overrideImpostorFovBonusChunks,
                continuousBreak = msg.continuousBreak,
                dominantHand = msg.dominantHand,
                disabledViewModes = msg.disabledViewModes,
                turnSpeedHorizontal = msg.turnSpeedHorizontal,
                turnSpeedVertical = msg.turnSpeedVertical,
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
                    gltfModel = def.gltfModel,
                    liquid = def.liquid,
                    viscosity = def.viscosity,
                    minimapVisible = def.minimapVisible,
                    rotatable = def.rotatable,
                    hasStuds = def.hasStuds,
                    brickSize = def.brickSize,
                    plainColorable = def.plainColorable,
                    isCubic = def.isCubic,
                    topColor = def.topColor,
                    sideColor = def.sideColor,
                    rail =
                        def.rail?.let { rail ->
                            RailInfo(
                                connections =
                                    rail.connections.map { group -> group.map { it.toString() } },
                                height = rail.height,
                            )
                        },
                )
            }
        val items =
            ItemRegistry.keys().associate { type ->
                val def = ItemRegistry.get(type)
                type.id to
                    ItemInfo(
                        buildable = def.buildable,
                        placesBlock = def.placesBlock?.id,
                        plainColor = def.plainColor,
                        consumable = def.healthRestore > 0 || def.manaRestore > 0,
                        spawnsEntity = def.spawnsEntity?.id,
                    )
            }
        val plainColors = PlainColorRegistry.all().map { PlainColorInfo(it.name, it.hex()) }
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
        val vehicles =
            VehicleRegistry.keys().associate { type ->
                type.id to VehicleRegistry.get(type)!!.bbmodelFile
            }
        val vehicleDefinitions =
            VehicleRegistry.keys().associate { type ->
                val def = VehicleRegistry.get(type)!!
                type.id to
                    VehicleCodexInfo(
                        bbmodelFile = def.bbmodelFile,
                        width = def.width,
                        height = def.height,
                        speed = def.speed,
                    )
            }
        val placeables =
            PlaceableRegistry.keys().associate { type ->
                type.id to PlaceableRegistry.get(type)!!.bbmodelFile
            }
        val placeableDefinitions =
            SiegeWeaponRegistry.keys().associate { type ->
                val def = SiegeWeaponRegistry.get(type)!!
                type.id to PlaceableCodexInfo(def.bbmodelFile, def.width, def.height)
            }
        val siegeProjectiles =
            SiegeProjectileRegistry.keys().associate { type ->
                type.id to SiegeProjectileRegistry.get(type)!!.bbmodelFile
            }
        val siegeProjectileDefinitions =
            SiegeProjectileRegistry.keys().associate { type ->
                val def = SiegeProjectileRegistry.get(type)!!
                type.id to SiegeProjectileCodexInfo(def.bbmodelFile, def.radius)
            }
        val siegeWeaponDefinitions =
            SiegeWeaponRegistry.keys().associate { type ->
                val def = SiegeWeaponRegistry.get(type)!!
                type.id to
                    SiegeWeaponCodexInfo(def.muzzleOffset, def.launchPower, def.launchPitchDeg)
            }
        return ServerMessage.RegistrySync(
            blocks,
            items,
            npcs,
            npcDefinitions,
            npcWalkBones,
            vehicles,
            vehicleDefinitions,
            plainColors,
            WorldConstants.IMPOSTOR_SKIRT_DEPTH,
            placeables,
            placeableDefinitions,
            siegeProjectiles,
            siegeProjectileDefinitions,
            siegeWeaponDefinitions)
    }

    private val reloadCoordinator =
        ReloadCoordinator(
            dropConfig = dropConfig,
            world = world,
            reloadBiomes = reloadBiomes,
            reloadRegistries = reloadRegistries,
            reloadGameConfig = reloadGameConfig,
            reloadFactions =
                reloadFactionsConfig?.let { loader ->
                    {
                        factionManager.applyConfig(loader())
                        factionManager.reconcile()
                    }
                },
            sessionRegistry = sessionRegistry,
            buildRegistrySync = ::buildRegistrySync,
            npcConfigLoader = npcConfigLoader,
            npcRegistryLoader = npcRegistryLoader,
            npcManager = npcManager,
            i18n = i18n,
            weatherManager = weatherManager,
            vegetationManager = vegetationManager,
            questManager = questManager,
            questRegistryLoader = questRegistryLoader,
            reloadRbac = reloadRbac,
            reloadArmorRegistry = {
                armorRegistry = armorRegistryLoader.load()
                weaponRegistry = weaponRegistryLoader.load()
                toolRegistry = toolRegistryLoader.load()
                val freshArmor = armorRegistry
                combatProcessor.reload(
                    combatConfig,
                    attackRegistry,
                    freshArmor,
                    classesData.classes,
                    weaponRegistry,
                    toolRegistry)
                regenProcessor.reload(
                    classesData, combatConfig.maxRage, freshArmor, weaponRegistry, toolRegistry)
                spellProcessor.reload(
                    spellRegistry,
                    classesData.classes,
                    freshArmor,
                    combatConfig,
                    weaponRegistry,
                    toolRegistry)
                statusEffectProcessor.reload(freshArmor, weaponRegistry, toolRegistry)
            },
            reloadEquipmentCategories = {
                weaponCategories = weaponCategoryRegistryLoader.load()
                toolCategories = toolCategoryRegistryLoader.load()
            },
            reloadRecipeRegistry = { RecipeRegistry.load(recipeRegistryLoader.load()) },
            reloadCombatSystems =
                if (combatConfigLoader != null ||
                    skillsConfigLoader != null ||
                    classesConfigLoader != null ||
                    experienceConfigLoader != null)
                    ::reloadCombatSystems
                else null,
        )

    suspend fun reload(lang: String): String = reloadCoordinator.reload(lang)

    fun start(app: Application) {
        appScope = app
        log.info("GameLoop starting (tick=${TICK_MS}ms, gravity=$GRAVITY)")
        validatePluginSystemIds(commands, discoverPlugins())
        PluginLoader.load().forEach { plugin ->
            log.info("Loaded plugin: {} ({})", plugin.name, plugin.id)
            plugin.commands().forEach { cmd ->
                check(cmd.command !in commands) {
                    "Plugin ${plugin.name}: command ${cmd.command} already registered"
                }
                commands[cmd.command] = cmd
            }
            pluginTickHandlers += plugin.tickHandlers()
        }
        RecipeRegistry.load(recipeRegistryLoader.load())
        armorRegistry = armorRegistryLoader.load()
        npcConfigLoader.load()
        npcManager.loadDefinitions(npcRegistryLoader.load())
        questRegistryLoader?.load()?.let { questManager?.reloadDefinitions(it) }
        gameWorld.loadPersistedState()
        app.launch {
            var nextTickAt = System.currentTimeMillis() + TICK_MS
            while (isActive) {
                val waitMs = nextTickAt - System.currentTimeMillis()
                if (waitMs > 0) delay(waitMs)
                nextTickAt = nextTickDeadline(System.currentTimeMillis(), nextTickAt, TICK_MS)
                runCatching { tick() }
                    .onFailure {
                        if (it is CancellationException) throw it
                        log.error("tick error: {}", it.message, it)
                    }
                saveTickCounter++
                if (saveTickCounter >= SAVE_INTERVAL_TICKS) {
                    saveTickCounter = 0
                    flushWorld()
                    gameWorld.saveMetadata()
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
        gameWorld.rebuildTerrainSync()
        gameWorld.saveMetadata()
        sessionRegistry.all().forEach { session -> savePlayer(session) }
        gameWorld.saveState()
        log.info("World saved on shutdown")
    }

    private fun savePlayer(session: PlayerSession) = playerPersister.save(session)

    private suspend fun handleConsumeItem(session: PlayerSession, itemType: ItemType) {
        val charData =
            session.characterData
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            i18n.t(session.state.language, "drink:server:no_character")))
                    return
                }
        val qty = session.inventory[itemType] ?: 0
        if (qty <= 0) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(
                        session.state.language,
                        "drink:server:not_in_inventory",
                        itemType.id.lowercase())))
            return
        }
        val def = ItemRegistry.get(itemType)
        if (def.healthRestore == 0 && def.manaRestore == 0) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(
                        session.state.language,
                        "drink:server:not_consumable",
                        itemType.id.lowercase())))
            return
        }
        if (qty == 1) session.inventory.remove(itemType) else session.inventory[itemType] = qty - 1
        val derived = DerivedStatsCalculator.compute(charData, emptyList())
        val newCharData =
            charData.copy(
                currentHp = (charData.currentHp + def.healthRestore).coerceAtMost(derived.maxHp),
                currentMana =
                    (charData.currentMana + def.manaRestore).coerceAtMost(derived.maxMana),
            )
        session.characterData = newCharData
        savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(
            combatProcessor.makeStatusUpdate(
                newCharData,
                derived,
                session.state.stance,
                session.combatState.attackCooldownUntilMs,
                session.combatState.attackCooldownsUntilMs,
                session.state.godMode))
        session.send(
            ServerMessage.Notification(
                i18n.t(session.state.language, "drink:server:consumed", itemType.id.lowercase())))
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

    private suspend fun tick(): Unit = tickProfiler.measure("total") { tickBody() }

    private suspend fun tickBody() {
        gameWorld.gameTicks++
        timeBroadcastCounter++
        if (timeBroadcastCounter >= TIME_BROADCAST_TICKS) {
            timeBroadcastCounter = 0
            val timeMsg = ServerMessage.TimeUpdate(gameWorld.gameTicks)
            sessionRegistry.all().forEach { it.send(timeMsg) }
        }

        tickProfiler.measure("players") {
            sessionRegistry.all().forEach { session ->
                val input = intentCollector.collect(session)
                blockBreaker.tick(session)
                val newState = movementProcessor.process(session, input)
                if (newState != session.state) {
                    session.state = newState
                    val update = ServerMessage.PlayerUpdate(newState, session.lastProcessedSeq)
                    sessionRegistry.all().forEach { it.send(update) }
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
        tickProfiler.measure("worldItems") { worldItems.tickCollection(sessionRegistry.all()) }
        gameTimeService.tick(TICK_SECONDS.toDouble())
        tickProfiler.measure("npc") {
            npcTickPipeline.tick(world, sessionRegistry.all(), combatProcessor)
        }
        tickProfiler.measure("vehicles") { vehicleTickPipeline.tick(world, sessionRegistry.all()) }
        tickProfiler.measure("siegeProjectiles") {
            siegeProjectileTickPipeline.tick(
                world, sessionRegistry.all(), npcManager, combatProcessor)
        }
        // In the tick, not in a wall-clock coroutine of its own: driving the slow lane from a
        // separate 5 s loop raced the main tick and gave the same arena a different spawn rate
        // depending on whether the live server or the simulator was running it.
        npcLifecycleTickCounter++
        if (npcLifecycleTickCounter >= NpcSubsystemFactory.LIFECYCLE_INTERVAL_TICKS) {
            npcLifecycleTickCounter = 0
            tickProfiler.measure("npcLifecycle") {
                runCatching { npcTickPipeline.lifecycle(world, sessionRegistry.all()) }
                    .onFailure { log.error("npc lifecycle error: {}", it.message, it) }
            }
        }
        tickProfiler.measure("statusEffects") { statusEffectProcessor.tick(sessionRegistry.all()) }
        tickProfiler.measure("regen") { regenProcessor.tick(sessionRegistry.all()) }
        tickProfiler.measure("weather") {
            weatherManager.tick(world) { msg -> sessionRegistry.all().forEach { it.send(msg) } }
        }
        tickProfiler.measure("liquid") {
            liquidManager.tick { msg -> sessionRegistry.all().forEach { it.send(msg) } }
        }
        tickProfiler.measure("vegetation") {
            vegetationManager.tick { msg -> sessionRegistry.all().forEach { it.send(msg) } }
        }
        tickProfiler.measure("auction") { auctionManager?.tick() }
        targetDistanceTickCounter++
        if (targetDistanceTickCounter >= TARGET_DISTANCE_REFRESH_TICKS) {
            targetDistanceTickCounter = 0
            sessionRegistry.all().forEach { session ->
                if (session.combatState.targetId != null) {
                    session.send(combatProcessor.buildTargetUpdate(session))
                }
            }
        }
        if (pluginTickHandlers.isNotEmpty()) {
            val ctx =
                TickContext(
                    gameTicks = gameWorld.gameTicks,
                    sessionRegistry = sessionRegistry,
                    world = world,
                    commandContext = commandContext,
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

        val accountEmail: String =
            if (tokenStore != null) {
                authResult!!.email
            } else {
                val email = userName
                if (!email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                    socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid email"))
                    return
                }
                noAuthAccountStore?.getOrCreate(email)
                email
            }

        val saved = persistence?.loadPlayerState(playerName)
        val id = saved?.id ?: UUID.randomUUID().toString()
        if (saved != null &&
            saved.email.isNotEmpty() &&
            !saved.email.equals(accountEmail, ignoreCase = true)) {
            socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "forbidden"))
            return
        }
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
                flying = saved?.flying ?: false,
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
                disabledViewModes = saved?.disabledViewModes ?: emptySet(),
                skin = resolveSkin(saved?.skin),
                armors = saved?.armors ?: emptyList(),
                ownedArmors = saved?.ownedArmors ?: emptyList(),
                ownedWeapons = saved?.ownedWeapons ?: emptyList(),
                ownedTools = saved?.ownedTools ?: emptyList(),
                animatedFavicon = saved?.animatedFavicon ?: true,
                chunkDebugVisible = saved?.chunkDebugVisible ?: false,
                statisticsVisible = saved?.statisticsVisible ?: false,
                attackPanelVisible = saved?.attackPanelVisible ?: false,
                fieldOfView = saved?.fieldOfView ?: 70,
                dynamicFogEnabled = saved?.dynamicFogEnabled ?: true,
                knownRecipes = saved?.knownRecipes ?: emptySet(),
                rpgOptOut =
                    if (saved?.characterData != null) false else (saved?.rpgOptOut ?: false),
                godMode = saved?.godMode ?: false,
                lightBoostEnabled = saved?.lightBoostEnabled ?: false,
                zoneLevel = saved?.zoneLevel ?: 0,
                quests = saved?.quests ?: emptyMap(),
                email = accountEmail,
                autoTargetEnabled = saved?.autoTargetEnabled ?: true,
                inventorySortA = saved?.inventorySortA ?: "",
                inventorySortB = saved?.inventorySortB ?: "",
                shadowAngleDeg = saved?.shadowAngleDeg ?: 1,
                wallet = saved?.wallet ?: 0L,
                overrideViewRadius = saved?.overrideViewRadius,
                overrideForwardViewRadius = saved?.overrideForwardViewRadius,
                overrideUseImpostor = saved?.overrideUseImpostor,
                overrideImpostorRadiusChunks = saved?.overrideImpostorRadiusChunks,
                overrideImpostorFovBonusChunks = saved?.overrideImpostorFovBonusChunks,
                continuousBreak = saved?.continuousBreak ?: false,
                dominantHand = saved?.dominantHand ?: Hand.RIGHT,
                turnSpeedHorizontal = saved?.turnSpeedHorizontal ?: 2.5f,
                turnSpeedVertical = saved?.turnSpeedVertical ?: 1.2f,
                rightHandItem = saved?.rightHandItem,
                leftHandItem = saved?.leftHandItem,
                pets = saved?.pets ?: emptyList(),
                activePetId = null,
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
        saved?.shortcutBarPages?.forEachIndexed { page, pageSlots ->
            if (page in 0..9)
                pageSlots.forEachIndexed { i, item ->
                    if (i in 0..9) session.shortcutBarPages[page][i] = item
                }
        }
        if (session.shortcutBarPages[0].all { it == null }) {
            saved?.shortcutBar?.forEachIndexed { i, item ->
                if (i in 0..9) session.shortcutBarPages[0][i] = item
            }
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
                chunkSection.transport,
                SERVER_BUILD_TIMESTAMP,
                MAX_INTERACTION_DISTANCE))
        session.send(buildRegistrySync())
        session.send(
            ServerMessage.RecipeSync(
                recipes = RecipeRegistry.all(),
                knownRecipes = session.knownRecipes.toSet(),
            ))
        session.send(buildPreferencesSync(session))
        if (session.hasPermission("admin")) {
            session.send(
                ServerMessage.InstanceZonesSync(instanceRegistry.all().map { it.toProto() }))
            session.send(
                ServerMessage.ScenesSync(
                    sceneRegistry.all().map {
                        SceneSummaryProto(it.id, it.name, it.width, it.height, it.depth)
                    }))
        }
        if (session.state.godMode) session.send(ServerMessage.GodModeUpdate(true))
        if (session.state.editMode == EditMode.CREATIVE) {
            session.send(ServerMessage.EditModeUpdate(session.state.editMode))
        }
        chatService.onPlayerConnect(session)
        session.state.guildId?.let { chatService.subscribe(session, "guild:$it") }
        session.state.factionId?.let { chatService.subscribe(session, "faction:$it") }
        groupManager.sendSync(session)
        guildManager.sendSync(session)
        factionManager.sendSync(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(ServerMessage.WalletUpdate(session.state.wallet))
        questManager?.sendQuestSync(session)
        mailManager?.let { session.send(ServerMessage.MailSync(it.loadForPlayer(playerName))) }
        claimManager.sendSync(session)
        session.send(ServerMessage.ShortcutBarUpdate(session.shortcutBarPages.toPageMap()))
        session.send(ServerMessage.TimeUpdate(gameWorld.gameTicks))
        val charData = session.characterData
        if (charData != null) {
            val bonuses =
                session.state.equipmentBonuses(armorRegistry, weaponRegistry, toolRegistry)
            val derived = DerivedStatsCalculator.compute(charData, bonuses)
            session.send(
                ServerMessage.CharacterSync(
                    charData,
                    derived,
                    DerivedStatsCalculator.effectiveBaseStats(charData, bonuses)))
            session.send(
                combatProcessor.makeStatusUpdate(
                    charData,
                    derived,
                    session.state.stance,
                    session.combatState.attackCooldownUntilMs,
                    session.combatState.attackCooldownsUntilMs,
                    session.state.godMode))
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
        petManager.rosterSyncFor(session)
        vehicleManager.sendAllTo(session)
        placeableManager.sendAllTo(session)
        siegeWeaponManager.sendAllTo(session)
        siegeProjectileManager.sendAllTo(session)
        // A stale session with the same id (e.g. a second client reconnecting under the same
        // playerName before the first socket's read loop noticed it's dead) would otherwise be
        // silently overwritten in the registry, leaving its socket alive but never ticked again.
        sessionRegistry[id]?.let {
            if (it !== session) {
                runCatching {
                    it.socket.close(
                        CloseReason(CloseReason.Codes.VIOLATED_POLICY, "replaced by new session"))
                }
            }
        }
        sessionRegistry[id] = session
        broadcastPlayerAdmin(
            """{"type":"playerJoined","id":"$id","name":${playerName.toPlayerAdminJson()},"x":${spawn.x},"y":${spawn.y},"z":${spawn.z},"yaw":0.0}""")

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
                                        is ClientMessage.VehicleInteract ->
                                            vehicleManager.handleInteract(msg.vehicleId)
                                        is ClientMessage.PlaceableInteract -> {
                                            placeableManager.handleInteract(msg.id, session)
                                            siegeWeaponManager.despawnFor(msg.id)
                                        }
                                        is ClientMessage.PlaceableRotate ->
                                            placeableManager.handleRotate(msg.id)
                                        is ClientMessage.SiegeWeaponSetPitch ->
                                            siegeWeaponManager.getByPlaceableId(msg.id)?.let {
                                                siegeWeaponManager.handleSetPitch(it.id, msg.value)
                                            }
                                        is ClientMessage.SiegeWeaponNudgePitch ->
                                            siegeWeaponManager.getByPlaceableId(msg.id)?.let {
                                                siegeWeaponManager.handleNudgePitch(session, it.id)
                                            }
                                        is ClientMessage.SiegeWeaponSetPower ->
                                            siegeWeaponManager.getByPlaceableId(msg.id)?.let {
                                                siegeWeaponManager.handleSetPower(it.id, msg.value)
                                            }
                                        is ClientMessage.SiegeWeaponNudgePower ->
                                            siegeWeaponManager.getByPlaceableId(msg.id)?.let {
                                                siegeWeaponManager.handleNudgePower(session, it.id)
                                            }
                                        is ClientMessage.SiegeWeaponFire ->
                                            siegeWeaponManager.fire(
                                                session,
                                                msg.weaponId,
                                                placeableManager,
                                                world,
                                                siegeProjectileManager)
                                        is ClientMessage.RequestScenePreview -> {
                                            if (session.hasPermission("admin")) {
                                                sceneRegistry.get(msg.sceneId)?.let { scene ->
                                                    session.send(
                                                        ServerMessage.ScenePreviewData(
                                                            scene.id,
                                                            scene.width,
                                                            scene.height,
                                                            scene.depth,
                                                            ScenePlacer.previewOccupancy(scene),
                                                            scene.states,
                                                        ))
                                                }
                                            }
                                        }
                                        is ClientMessage.RunMacro -> handleRunMacro(session, msg)
                                        is ClientMessage.RunMacroContent ->
                                            handleRunMacroContent(session, msg)
                                        is ClientMessage.SendMail ->
                                            mailManager?.handleSendMail(session, msg)
                                        is ClientMessage.MarkMailSeen ->
                                            mailManager?.handleMarkSeen(session, msg.mailId)
                                        is ClientMessage.DeleteMail ->
                                            mailManager?.handleDelete(session, msg.mailId)
                                        is ClientMessage.ClaimMailAttachments ->
                                            mailManager?.handleClaimAttachments(session, msg.mailId)
                                        is ClientMessage.AuctionCreateListing ->
                                            auctionManager?.createListing(
                                                session,
                                                msg.itemType,
                                                msg.quantity,
                                                msg.duration,
                                                msg.startingPrice,
                                                msg.buyNowPrice)
                                        is ClientMessage.AuctionPlaceBid ->
                                            auctionManager?.placeBid(
                                                session, msg.listingId, msg.amount)
                                        is ClientMessage.AuctionBuyNow ->
                                            auctionManager?.buyNow(session, msg.listingId)
                                        is ClientMessage.AuctionCancelListing ->
                                            auctionManager?.cancel(session, msg.listingId)
                                        is ClientMessage.AuctionSetFilter ->
                                            auctionManager?.setFilter(session, msg.filter)
                                        is ClientMessage.ClaimCreate ->
                                            claimManager.createClaim(session, msg.pos1, msg.pos2)
                                        is ClientMessage.ClaimAbandon ->
                                            claimManager.abandonClaim(session, msg.claimId)
                                        is ClientMessage.ClaimSetTrusted ->
                                            claimManager.setTrusted(
                                                session, msg.claimId, msg.playerName, msg.trusted)
                                        is ClientMessage.GroupCreate -> groupManager.create(session)
                                        is ClientMessage.GroupInvite ->
                                            groupManager.invite(session, msg.targetName)
                                        is ClientMessage.GroupInviteRespond ->
                                            groupManager.respondInvite(
                                                session, msg.groupId, msg.accept)
                                        is ClientMessage.GroupLeave -> groupManager.leave(session)
                                        is ClientMessage.GroupKick ->
                                            groupManager.kick(session, msg.targetId)
                                        is ClientMessage.GroupTransfer ->
                                            groupManager.transfer(session, msg.targetId)
                                        is ClientMessage.GroupDisband ->
                                            groupManager.disband(session)
                                        is ClientMessage.GuildCreate ->
                                            guildManager.create(session, msg.name, msg.tag)
                                        is ClientMessage.GuildInvite ->
                                            guildManager.invite(session, msg.targetName)
                                        is ClientMessage.GuildInviteRespond ->
                                            guildManager.respondInvite(
                                                session, msg.guildId, msg.accept)
                                        is ClientMessage.GuildLeave -> guildManager.leave(session)
                                        is ClientMessage.GuildKick ->
                                            guildManager.kick(session, msg.targetId)
                                        is ClientMessage.GuildSetMotd ->
                                            guildManager.setMotd(session, msg.text)
                                        is ClientMessage.GuildSetRank ->
                                            guildManager.setRank(
                                                session, msg.targetId, msg.rankName)
                                        is ClientMessage.GuildRankUpsert ->
                                            guildManager.upsertRank(session, msg.rank)
                                        is ClientMessage.GuildRankDelete ->
                                            guildManager.deleteRank(session, msg.rankName)
                                        is ClientMessage.GuildTransferOwner ->
                                            guildManager.transferOwner(session, msg.targetId)
                                        is ClientMessage.GuildDisband ->
                                            guildManager.disband(session)
                                        is ClientMessage.GuildBankDeposit ->
                                            guildManager.bankDeposit(
                                                session, msg.itemType, msg.count)
                                        is ClientMessage.GuildBankWithdraw ->
                                            guildManager.bankWithdraw(
                                                session, msg.itemType, msg.count)
                                        is ClientMessage.FactionSetAffiliation ->
                                            factionManager.setAffiliation(session, msg.factionId)
                                        is ClientMessage.CreativeCameraFocus -> {
                                            if (session.state.editMode == EditMode.CREATIVE) {
                                                session.creativeFocusPos = msg.x to msg.z
                                            }
                                        }
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
            // A session evicted by a newer reconnect under the same id (see the replace logic
            // above) must not tear down the state of the session that replaced it.
            if (sessionRegistry[id] === session) {
                broadcastPlayerAdmin("""{"type":"playerLeft","id":"$id"}""")
                sessionRegistry.remove(id)
                chunkStreamer.cleanupSession(id)
                petManager.onPlayerDisconnected(session)
                npcManager.clearPlayer(id)
                vehicleManager.clearRider(id)
                npcTickPipeline.onPlayerDisconnected(sessionRegistry.all())
                tradeManager.onPlayerDisconnect(id)
                auctionManager?.clearFilter(id)
                groupManager.onDisconnect(session)
                savePlayer(session)
                log.info(
                    "player disconnected: {} name={} (total={})",
                    id.take(8),
                    session.state.name,
                    sessionRegistry.size)
                val left = ServerMessage.PlayerLeft(id)
                sessionRegistry.all().forEach { it.send(left) }
            } else {
                log.info(
                    "stale session closed: {} name={} (replaced by newer session)",
                    id.take(8),
                    session.state.name)
            }
        }
    }

    suspend fun onChunkConnect(socket: DefaultWebSocketSession) {
        val firstFrame =
            runCatching {
                    val frame = socket.incoming.receive()
                    if (frame is Frame.Text) frame.readText().trim() else null
                }
                .getOrNull() ?: return
        val playerId =
            if (tokenStore != null) {
                val authResult = tokenStore.validate(firstFrame)
                if (authResult == null) {
                    socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid token"))
                    return
                }
                authResult.playerId
            } else {
                firstFrame
            }
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
