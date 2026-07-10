package org.micoli.micraft

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import java.io.File
import java.nio.file.Path
import org.koin.core.parameter.parametersOf
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.auth.LocalAuthProvider
import org.micoli.micraft.auth.OAuthProvider
import org.micoli.micraft.auth.installAuthRoutes
import org.micoli.micraft.auth.loadGroupsConfig
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.di.OptionalAuthProvider
import org.micoli.micraft.di.OptionalTokenStore
import org.micoli.micraft.di.OptionalWorldPersistence
import org.micoli.micraft.di.PlayerPersister
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.di.appModules
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.ServerConfig
import org.micoli.micraft.game.applyServerConfig
import org.micoli.micraft.game.armor.ArmorRegistryLoader
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.StatusEffectProcessor
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.item.ItemRegistryLoader
import org.micoli.micraft.game.loadServerConfig
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.npc.NpcSpawner
import org.micoli.micraft.game.recipe.RecipeRegistryLoader
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.game.session.NetworkStats
import org.micoli.micraft.game.tick.ChunkStreamer
import org.micoli.micraft.game.tick.MovementProcessor
import org.micoli.micraft.game.trade.TradeConfigLoader
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.biome.BiomeRegistry
import org.micoli.micraft.game.world.biome.loadBiomeRegistry
import org.micoli.micraft.game.world.block.BlockBreaker
import org.micoli.micraft.game.world.block.BlockPlacer
import org.micoli.micraft.game.world.block.BlockRegistryLoader
import org.micoli.micraft.game.world.house.loadHouseConfig
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.road.loadRoadConfig
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.game.world.weather.WeatherConfig
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.http.ArmorsController
import org.micoli.micraft.http.AssetManifestController
import org.micoli.micraft.http.AssetNotifyController
import org.micoli.micraft.http.AttacksController
import org.micoli.micraft.http.AutocompleteController
import org.micoli.micraft.http.BiomesController
import org.micoli.micraft.http.CharacterController
import org.micoli.micraft.http.ChunkController
import org.micoli.micraft.http.I18nController
import org.micoli.micraft.http.ItemsController
import org.micoli.micraft.http.KeybindingsController
import org.micoli.micraft.http.LayoutController
import org.micoli.micraft.http.MacrosController
import org.micoli.micraft.http.MapController
import org.micoli.micraft.http.MetricsController
import org.micoli.micraft.http.PlayerArmorsController
import org.micoli.micraft.http.PlayerRpgController
import org.micoli.micraft.http.PlayerSkinController
import org.micoli.micraft.http.SkinsController
import org.micoli.micraft.http.TerrainCache
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Application")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

val dataPath = "data"
val configDir: Path = Path.of("$dataPath/config")
val resourcesConfigDir: Path = Path.of("resources/config")

fun Application.module() {
    install(WebSockets) {}
    install(Koin) { modules(appModules) }

    val serverConfig = get<ServerConfig>()
    val gameConfig = get<GameConfig>()

    val debugWorld = gameConfig.debugWorld

    val persistence = get<OptionalWorldPersistence>().value
    val blockRegistryLoader = get<BlockRegistryLoader>()
    val itemRegistryLoader = get<ItemRegistryLoader>()

    val biomeFile = Path.of(dataPath + "/config/biomes.yaml")
    val biomeResourcesFile = resourcesConfigDir.resolve("biomes.yaml")
    val roadConfigPath = Path.of(dataPath + "/config/roads.yaml")
    val roadResourcesFile = resourcesConfigDir.resolve("roads.yaml")
    val houseConfigPath = Path.of(dataPath + "/config/houses.yaml")
    val houseResourcesFile = resourcesConfigDir.resolve("houses.yaml")

    val reloadBiomes: (() -> ChunkGenerator)? =
        if (!debugWorld) {
            {
                ProceduralChunkGenerator(
                    seed = 42L,
                    biomeRegistry = loadBiomeRegistry(biomeFile, biomeResourcesFile),
                    roadConfig = loadRoadConfig(roadConfigPath, roadResourcesFile),
                    houseConfig = loadHouseConfig(houseConfigPath, houseResourcesFile),
                )
            }
        } else null

    val reloadRegistries: () -> Unit = {
        BlockRegistry.load(blockRegistryLoader.reload())
        ItemRegistry.load(itemRegistryLoader.reload())
    }

    val reloadGameConfigLambda: () -> Unit = {
        validateYamlConfig(configDir.resolve("server.yaml"), "server.schema.json")
        applyServerConfig(
            loadServerConfig(
                Path.of(dataPath + "/config/server.yaml"),
                resourcesConfigDir.resolve("server.yaml")))
    }

    val authConfig = serverConfig.auth
    val groupsResourcesFile = resourcesConfigDir.resolve("groups.yaml")
    val groupsConfig = get<GroupsConfig>()
    val authProvider = get<OptionalAuthProvider>().value
    val tokenStore = get<OptionalTokenStore>().value

    val groupsFilePath = Path.of(authConfig.local.groupsFile)
    validateYamlConfig(groupsFilePath, "groups.schema.json")
    val reloadRbacLambda: (() -> Unit)? =
        when (val p = authProvider) {
            is LocalAuthProvider -> {
                { p.groupsConfig = loadGroupsConfig(groupsFilePath, groupsResourcesFile) }
            }
            is OAuthProvider -> {
                { p.groupsConfig = loadGroupsConfig(groupsFilePath, groupsResourcesFile) }
            }
            else -> null
        }

    val world = get<WorldState>()
    val biomeRegistry = get<BiomeRegistry>()
    val sessionRegistry = get<SessionRegistry>()
    val gameLoop =
        GameLoop(
            world,
            persistence,
            reloadBiomes,
            reloadRegistries = reloadRegistries,
            reloadGameConfig = reloadGameConfigLambda,
            i18n = get<I18nConfig>(),
            tokenStore = tokenStore,
            authProvider = authProvider,
            groupsConfig = groupsConfig,
            reloadRbac = reloadRbacLambda,
            chunkSection = serverConfig.chunks,
            sessionRegistry = sessionRegistry,
            playerPersister = get<PlayerPersister>(),
            chatChannelManager = get<ChatChannelManager>(),
            chatService = get<ChatService>(),
            dropConfig = get<DropConfig>(),
            worldItems = get<WorldItemManager>(),
            weatherConfig = get<WeatherConfig>(),
            weatherManager = get<WeatherManager>(),
            configRegistry = get<ConfigRegistry>(),
            liquidManager = get<LiquidManager>(),
            vegetationConfig = get<VegetationConfig>(),
            vegetationManager = get<VegetationManager>(),
            recipeRegistryLoader = get<RecipeRegistryLoader>(),
            armorRegistryLoader = get<ArmorRegistryLoader>(),
            npcConfigLoader = get<NpcConfigLoader>(),
            npcRegistryLoader = get<NpcRegistryLoader>(),
            npcManager = get<NpcManager>(),
            npcSpawner = get<NpcSpawner>(),
            combatConfig = get<CombatConfigData>(),
            attackRegistry = get<Map<String, AttackDefinition>>(),
            combatProcessor = get<CombatProcessor>(),
            statusEffectProcessor = get<StatusEffectProcessor>(),
            tradeConfigLoader = get<TradeConfigLoader>(),
            tradeManager = get<TradeManager>(),
            blockBreaker = get<BlockBreaker>(),
            blockPlacer = get<BlockPlacer>(),
            movementProcessor = get<MovementProcessor>(),
            chunkStreamer = get<ChunkStreamer>(),
            terrainCache = get<TerrainCache>(),
            networkStats = get<NetworkStats>(),
            commandContextFactory = { closures -> get<CommandContext> { parametersOf(closures) } },
            experienceProcessor = get<ExperienceProcessor>(),
        )
    gameLoop.start(this)
    installAuthRoutes(
        authConfig.provider, authProvider, tokenStore, serverConfig.network.messageEncoder)

    Runtime.getRuntime().addShutdownHook(Thread { gameLoop.shutdown() })

    routing {
        val webBuildDir = System.getenv("MICRAFT_WEB_DIST")
        if (webBuildDir != null) {
            staticFiles("/", File("$webBuildDir/kotlin-webpack/wasmJs/developmentExecutable"))
        }
        val assetManifest = AssetManifestController(webBuildDir)
        assetManifest.register(this)
        val assetNotify = AssetNotifyController(assetManifest)
        assetNotify.register(this)
        assetNotify.start(this@module)
        KeybindingsController(persistence, dataPath).register(this)
        AutocompleteController(gameLoop).register(this)
        I18nController(gameLoop).register(this)
        LayoutController().register(this)
        ItemsController().register(this)
        AttacksController(gameLoop).register(this)
        MacrosController().register(this)
        BiomesController(biomeRegistry).register(this)
        PlayerSkinController(persistence).register(this)
        PlayerArmorsController(persistence).register(this)
        PlayerRpgController(persistence).register(this)
        CharacterController(persistence).register(this)
        SkinsController().register(this)
        ArmorsController(dataPath).register(this)
        staticFiles("/api/models", File("resources"))
        MapController(gameLoop).register(this)
        MetricsController(gameLoop).register(this)
        ChunkController(world, tokenStore, serverConfig.chunks.httpWorkers).register(this)
        webSocket("/game") { gameLoop.onConnect(this) }
        webSocket("/chunks") { gameLoop.onChunkConnect(this) }
    }
}
