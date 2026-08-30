package org.micoli.micraft

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.OutputFormat
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
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
import org.koin.core.qualifier.named
import org.koin.ksp.generated.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.auth.LocalAuthProvider
import org.micoli.micraft.auth.OAuthProvider
import org.micoli.micraft.auth.installAuthRoutes
import org.micoli.micraft.auth.loadGroupsConfig
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.di.AppModule
import org.micoli.micraft.di.OptionalAuctionManager
import org.micoli.micraft.di.OptionalAuthProvider
import org.micoli.micraft.di.OptionalNoAuthAccountStore
import org.micoli.micraft.di.OptionalTokenStore
import org.micoli.micraft.di.OptionalWorldPersistence
import org.micoli.micraft.di.PlayerPersister
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.di.loadRegistries
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.ServerConfig
import org.micoli.micraft.game.applyServerConfig
import org.micoli.micraft.game.armor.ArmorRegistryLoader
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.classes.ClassesConfigData
import org.micoli.micraft.game.combat.CombatConfigData
import org.micoli.micraft.game.combat.CombatProcessor
import org.micoli.micraft.game.combat.RegenProcessor
import org.micoli.micraft.game.combat.SpellProcessor
import org.micoli.micraft.game.combat.StatusEffectProcessor
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.item.ItemRegistryLoader
import org.micoli.micraft.game.loadServerConfig
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.npc.NpcSpawner
import org.micoli.micraft.game.npc.NpcSubsystemFactory
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileManager
import org.micoli.micraft.game.placeable.siege.SiegeProjectileRegistryLoader
import org.micoli.micraft.game.placeable.siege.SiegeWeaponManager
import org.micoli.micraft.game.placeable.siege.SiegeWeaponRegistryLoader
import org.micoli.micraft.game.plaincolor.PlainColorRegistryLoader
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestRegistryLoader
import org.micoli.micraft.game.recipe.RecipeRegistryLoader
import org.micoli.micraft.game.rpg.ExperienceConfigData
import org.micoli.micraft.game.rpg.ExperienceProcessor
import org.micoli.micraft.game.session.NetworkStats
import org.micoli.micraft.game.tick.ChunkStreamer
import org.micoli.micraft.game.tick.MovementProcessor
import org.micoli.micraft.game.trade.TradeConfigLoader
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.vehicle.VehicleManager
import org.micoli.micraft.game.vehicle.VehicleRegistryLoader
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
import org.micoli.micraft.http.AdminController
import org.micoli.micraft.http.ArmorsController
import org.micoli.micraft.http.AssetManifestController
import org.micoli.micraft.http.AssetNotifyController
import org.micoli.micraft.http.AttacksController
import org.micoli.micraft.http.AuctionsController
import org.micoli.micraft.http.AutocompleteController
import org.micoli.micraft.http.BiomesController
import org.micoli.micraft.http.CharacterController
import org.micoli.micraft.http.ChunkController
import org.micoli.micraft.http.DocsController
import org.micoli.micraft.http.GameAssetsController
import org.micoli.micraft.http.I18nController
import org.micoli.micraft.http.ItemsController
import org.micoli.micraft.http.KeybindingsController
import org.micoli.micraft.http.LayoutController
import org.micoli.micraft.http.MacrosController
import org.micoli.micraft.http.MapController
import org.micoli.micraft.http.MetricsController
import org.micoli.micraft.http.PlayerArmorsController
import org.micoli.micraft.http.PlayerHandsController
import org.micoli.micraft.http.PlayerOwnedController
import org.micoli.micraft.http.PlayerRpgController
import org.micoli.micraft.http.PlayerSkinController
import org.micoli.micraft.http.PlayersController
import org.micoli.micraft.http.QuestsController
import org.micoli.micraft.http.ScreenshotController
import org.micoli.micraft.http.ServerInfoController
import org.micoli.micraft.http.SiegeWeaponsController
import org.micoli.micraft.http.SimulationController
import org.micoli.micraft.http.SkinsController
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.http.ToolsController
import org.micoli.micraft.http.VehiclesController
import org.micoli.micraft.http.WeaponsController
import org.micoli.micraft.simulation.SimulationDeps
import org.micoli.micraft.simulation.SimulationRegistry
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Application")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

@kotlinx.serialization.Serializable data class PlayerByEmailEntry(val name: String, val id: String)

val dataPath = "data"
val configDir: Path = Path.of("$dataPath/config")
val resourcesConfigDir: Path = Path.of("resources/config")

fun Application.module() {
    install(WebSockets) {}
    install(Koin) { modules(AppModule().module) }
    install(OpenApi) {
        info {
            title = "MiCraft API"
            version = "1.0.0"
            description = "Server HTTP API — auth, game entities, admin, map/chunks."
        }
        outputFormat = OutputFormat.YAML
        // The plugin walks the whole routing tree regardless of annotation, so SPA/static
        // pages, websockets (/game, /chunks) and Prometheus/health endpoints (/metrics,
        // /status) must be excluded explicitly rather than by simply not annotating them.
        pathFilter = { _, segments ->
            segments.firstOrNull() in setOf("api", "auth") && segments != listOf("api", "docs")
        }
    }

    val serverConfig = get<ServerConfig>()
    val gameConfig = get<GameConfig>()

    val persistence = get<OptionalWorldPersistence>().value
    val blockRegistryLoader = get<BlockRegistryLoader>()
    val itemRegistryLoader = get<ItemRegistryLoader>()
    val plainColorRegistryLoader = get<PlainColorRegistryLoader>()
    val vehicleRegistryLoader = get<VehicleRegistryLoader>()
    val siegeWeaponRegistryLoader = get<SiegeWeaponRegistryLoader>()
    val siegeProjectileRegistryLoader = get<SiegeProjectileRegistryLoader>()

    val biomeFile = Path.of(dataPath + "/config/biomes.yaml")
    val biomeResourcesFile = resourcesConfigDir.resolve("biomes.yaml")
    val roadConfigPath = Path.of(dataPath + "/config/roads.yaml")
    val roadResourcesFile = resourcesConfigDir.resolve("roads.yaml")
    val houseConfigPath = Path.of(dataPath + "/config/houses.yaml")
    val houseResourcesFile = resourcesConfigDir.resolve("houses.yaml")

    val reloadBiomes: () -> ChunkGenerator = {
        ProceduralChunkGenerator(
            seed = gameConfig.worldSeed,
            biomeRegistry = loadBiomeRegistry(biomeFile, biomeResourcesFile),
            roadConfig = loadRoadConfig(roadConfigPath, roadResourcesFile),
            houseConfig = loadHouseConfig(houseConfigPath, houseResourcesFile),
        )
    }

    val reloadRegistries: () -> Unit = {
        loadRegistries(
            blockRegistryLoader,
            itemRegistryLoader,
            plainColorRegistryLoader,
            vehicleRegistryLoader,
            siegeWeaponRegistryLoader,
            siegeProjectileRegistryLoader)
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
    val noAuthAccountStore = get<OptionalNoAuthAccountStore>().value

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
            factionsSection = serverConfig.factions,
            reloadFactionsConfig = {
                loadServerConfig(
                        Path.of(dataPath + "/config/server.yaml"),
                        resourcesConfigDir.resolve("server.yaml"))
                    .factions
            },
            i18n = get<I18nConfig>(),
            tokenStore = tokenStore,
            authProvider = authProvider,
            groupsConfig = groupsConfig,
            reloadRbac = reloadRbacLambda,
            chunkSection = serverConfig.chunks,
            noAuthAccountStore = noAuthAccountStore,
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
            instanceRegistry = get<org.micoli.micraft.game.world.instance.InstanceRegistry>(),
            claimRegistry = get<org.micoli.micraft.game.world.claim.ClaimRegistry>(),
            claimManager = get<org.micoli.micraft.game.world.claim.ClaimManager>(),
            sceneRegistry = get<org.micoli.micraft.game.world.scene.SceneRegistry>(),
            vegetationConfig = get<VegetationConfig>(),
            vegetationManager = get<VegetationManager>(),
            recipeRegistryLoader = get<RecipeRegistryLoader>(),
            armorRegistryLoader = get<ArmorRegistryLoader>(),
            npcConfigLoader = get<NpcConfigLoader>(),
            npcRegistryLoader = get<NpcRegistryLoader>(),
            npcSubsystemFactory = get<NpcSubsystemFactory>(),
            npcManager = get<NpcManager>(),
            npcSpawner = get<NpcSpawner>(),
            vehicleManager = get<VehicleManager>(),
            placeableManager = get<PlaceableManager>(),
            siegeWeaponManager = get<SiegeWeaponManager>(),
            siegeProjectileManager = get<SiegeProjectileManager>(),
            combatConfig = get<CombatConfigData>(),
            attackRegistry = get(named("attacks")),
            spellRegistry = get(named("spells")),
            classesData = get<ClassesConfigData>(),
            combatConfigLoader = get<org.micoli.micraft.game.combat.CombatConfig>(),
            skillsConfigLoader = get<org.micoli.micraft.game.combat.SkillsConfig>(),
            classesConfigLoader = get<org.micoli.micraft.game.classes.ClassesConfig>(),
            experienceConfigLoader = get<org.micoli.micraft.game.rpg.ExperienceConfig>(),
            combatProcessor = get<CombatProcessor>(),
            statusEffectProcessor = get<StatusEffectProcessor>(),
            regenProcessor = get<RegenProcessor>(),
            spellProcessor = get<SpellProcessor>(),
            tradeConfigLoader = get<TradeConfigLoader>(),
            tradeManager = get<TradeManager>(),
            auctionManager = get<OptionalAuctionManager>().value,
            blockBreaker = get<BlockBreaker>(),
            blockPlacer = get<BlockPlacer>(),
            movementProcessor = get<MovementProcessor>(),
            chunkStreamer = get<ChunkStreamer>(),
            terrainCache = get<TerrainCache>(),
            networkStats = get<NetworkStats>(),
            commandContextFactory = { closures -> get<CommandContext> { parametersOf(closures) } },
            experienceProcessor = get<ExperienceProcessor>(),
            petManager = get<org.micoli.micraft.game.pet.PetManager>(),
            petCoordinator = get<org.micoli.micraft.game.pet.PetCoordinator>(),
            questManager = get<QuestManager>(),
            questRegistryLoader = get<QuestRegistryLoader>(),
        )
    gameLoop.start(this)
    installAuthRoutes(
        authConfig.provider,
        authProvider,
        tokenStore,
        serverConfig.network.messageEncoder,
        noAuthAccountStore)

    Runtime.getRuntime().addShutdownHook(Thread { gameLoop.shutdown() })

    val questManager = get<QuestManager>()

    // Resolved here, not inside `routing {}`: there `get(...)` binds to the Routing DSL instead of
    // the Koin container. The lambda re-reads the live NPC registry on every simulation start.
    val combatConfigData = get<CombatConfigData>()
    val simulationAttackRegistry =
        get<Map<String, org.micoli.micraft.combat.AttackDefinition>>(named("attacks"))
    val armorRegistryLoader = get<ArmorRegistryLoader>()
    val simulationI18n = get<I18nConfig>()
    val simulationVegetationConfig = get<VegetationConfig>()
    val simulationExperienceConfig = get<ExperienceConfigData>()
    val simulationSpellRegistry =
        get<Map<String, org.micoli.micraft.game.combat.SpellDefinition>>(named("spells"))
    val simulationRegistry = SimulationRegistry {
        SimulationDeps(
            definitions = gameLoop.getNpcManager().getDefinitions(),
            combatConfig = combatConfigData,
            attackRegistry = simulationAttackRegistry,
            armorRegistry = armorRegistryLoader.load(),
            classRegistry = gameLoop.classRegistry,
            weaponRegistry = get(),
            toolRegistry = get(),
            i18n = simulationI18n,
            vegetationConfig = simulationVegetationConfig,
            experienceConfig = simulationExperienceConfig,
            spellRegistry = simulationSpellRegistry,
        )
    }

    val npcRegistryLoaderForSim = get<NpcRegistryLoader>()

    routing {
        route("api.yaml") { openApi() }

        // MICRAFT_WEB_DIST points directly at the served executable dir
        // (…/kotlin-webpack/wasmJs/developmentExecutable or …/productionExecutable).
        // Legacy fallback: if it points at the module build dir, descend into the dev subdir.
        val webBuildDir = System.getenv("MICRAFT_WEB_DIST")
        var servedDir: File? = null
        if (webBuildDir != null) {
            val base = File(webBuildDir)
            val served =
                if (File(base, "index.html").exists()) base
                else File(base, "kotlin-webpack/wasmJs/developmentExecutable")
            servedDir = served
            staticFiles("/", served)
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
        ServerInfoController().register(this)
        AttacksController(gameLoop).register(this)
        MacrosController().register(this)
        BiomesController(biomeRegistry).register(this)
        PlayerSkinController(persistence).register(this)
        ScreenshotController(dataPath).register(this)
        PlayerArmorsController(persistence, sessionRegistry).register(this)
        PlayerHandsController(persistence, sessionRegistry).register(this)
        PlayerOwnedController(persistence, sessionRegistry).register(this)
        PlayerRpgController(persistence).register(this)
        CharacterController(persistence).register(this)
        SkinsController(dataPath).register(this)
        VehiclesController(dataPath).register(this)
        ArmorsController(dataPath).register(this)
        WeaponsController(dataPath).register(this)
        ToolsController(dataPath).register(this)
        SiegeWeaponsController(dataPath).register(this)
        AuctionsController(gameLoop, tokenStore).register(this)
        GameAssetsController().register(this)
        QuestsController(questManager).register(this)
        PlayersController(gameLoop.getMailManager()).register(this)
        staticFiles("/api/models", File("resources"))
        MapController(gameLoop, tokenStore).register(this)
        MetricsController(gameLoop).register(this)
        DocsController().register(this)
        val adminController =
            AdminController(
                authProvider as? LocalAuthProvider,
                noAuthAccountStore,
                persistence,
                gameLoop,
                dataPath,
                tokenStore)
        adminController.register(this)
        adminController.registerAdminWs(this)
        adminController.registerEditWs(this)
        Runtime.getRuntime().addShutdownHook(Thread { simulationRegistry.stopAll() })
        val simulationController =
            SimulationController(
                registry = simulationRegistry,
                npcTypesProvider = { gameLoop.getNpcManager().getDefinitions().keys.sorted() },
                npcRegistryLoader = npcRegistryLoaderForSim,
                tokenStore = tokenStore,
            )
        simulationController.register(this)
        simulationController.registerWs(this)
        ChunkController(world, tokenStore, serverConfig.chunks.httpWorkers).register(this)
        get(
            "/api/players/by-email/{email}",
            {
                description = "Player characters (name + id) linked to an account email"
                request { pathParameter<String>("email") { description = "Account email" } }
                response {
                    code(HttpStatusCode.OK) { body<List<PlayerByEmailEntry>>() }
                    code(HttpStatusCode.BadRequest) { description = "Missing email" }
                }
            }) {
                val email =
                    call.parameters["email"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val players = persistence?.listPlayersByEmail(email) ?: emptyList()
                val json =
                    players.joinToString(",", "[", "]") {
                        """{"name":"${it.name.replace("\"", "\\\"")}","id":"${it.id}"}"""
                    }
                call.respondText(json, ContentType.Application.Json)
            }
        webSocket("/game") { gameLoop.onConnect(this, call.request.queryParameters["gameSession"]) }
        webSocket("/chunks") {
            gameLoop.onChunkConnect(this, call.request.queryParameters["gameSession"])
        }
        servedDir?.let { dir ->
            val indexFile = File(dir, "index.html")
            listOf("/", "/auth", "/chars", "/char-create", "/char-rpg-create").forEach { path ->
                get(path) { call.respondFile(indexFile) }
            }
            get("/game") { call.respondRedirect("/", permanent = false) }
            get("/game/") { call.respondRedirect("/", permanent = false) }
            get("/game/{accountEmail}/{charId}") { call.respondFile(indexFile) }
        }
    }
}
