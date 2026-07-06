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
import java.nio.file.Path
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.koin.core.parameter.parametersOf
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.auth.LocalAuthProvider
import org.micoli.micraft.auth.OAuthProvider
import org.micoli.micraft.auth.installAuthRoutes
import org.micoli.micraft.auth.loadGroupsConfig
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.CombatConfig
import org.micoli.micraft.combat.CombatProcessor
import org.micoli.micraft.combat.StatusEffectProcessor
import org.micoli.micraft.command.availablePlayerSkins
import org.micoli.micraft.di.OptionalAuthProvider
import org.micoli.micraft.di.OptionalTokenStore
import org.micoli.micraft.di.OptionalWorldPersistence
import org.micoli.micraft.di.PlayerPersister
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.di.appModules
import org.micoli.micraft.http.TerrainCache
import org.micoli.micraft.http.chunkRoutes
import org.micoli.micraft.http.mapRoutes
import org.micoli.micraft.http.metricsRoutes
import org.micoli.micraft.npc.NpcConfigLoader
import org.micoli.micraft.npc.NpcManager
import org.micoli.micraft.npc.NpcSpawner
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.session.NetworkStats
import org.micoli.micraft.tick.BlockBreaker
import org.micoli.micraft.tick.BlockPlacer
import org.micoli.micraft.tick.ChunkStreamer
import org.micoli.micraft.tick.LiquidManager
import org.micoli.micraft.tick.MovementProcessor
import org.micoli.micraft.tick.VegetationManager
import org.micoli.micraft.trade.TradeConfigLoader
import org.micoli.micraft.trade.TradeManager
import org.micoli.micraft.ui.WIDGET_REGISTRY
import org.micoli.micraft.ui.WidgetRegistryEntry
import org.micoli.micraft.world.ArmorDefinition
import org.micoli.micraft.world.ArmorRegistryLoader
import org.micoli.micraft.world.BiomeRegistry
import org.micoli.micraft.world.BlockRegistry
import org.micoli.micraft.world.BlockRegistryLoader
import org.micoli.micraft.world.ChatChannelManager
import org.micoli.micraft.world.ChatService
import org.micoli.micraft.world.DropConfig
import org.micoli.micraft.world.GameConfig
import org.micoli.micraft.world.I18nConfig
import org.micoli.micraft.world.ItemRegistry
import org.micoli.micraft.world.ItemRegistryLoader
import org.micoli.micraft.world.NpcRegistryLoader
import org.micoli.micraft.world.RecipeRegistryLoader
import org.micoli.micraft.world.ServerConfig
import org.micoli.micraft.world.VegetationConfig
import org.micoli.micraft.world.WeatherConfig
import org.micoli.micraft.world.WeatherManager
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldState
import org.micoli.micraft.world.applyServerConfig
import org.micoli.micraft.world.loadBiomeRegistry
import org.micoli.micraft.world.loadHouseConfig
import org.micoli.micraft.world.loadKeyBindings
import org.micoli.micraft.world.loadRoadConfig
import org.micoli.micraft.world.loadServerConfig
import org.micoli.micraft.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.world.validateYamlConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Application")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

private val SERVER_ID: String = UUID.randomUUID().toString()
val dataPath = "data"
val configDir = Path.of(dataPath + "/config")
val resourcesConfigDir = Path.of("resources/config")

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
            combatConfig = get<CombatConfig>(),
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
        )
    gameLoop.start(this)
    installAuthRoutes(authConfig.provider, authProvider, tokenStore)

    Runtime.getRuntime().addShutdownHook(Thread { gameLoop.shutdown() })

    routing {
        val webBuildDir = System.getenv("MICRAFT_WEB_DIST")
        if (webBuildDir != null) {
            staticFiles(
                "/", java.io.File("$webBuildDir/kotlin-webpack/wasmJs/developmentExecutable"))
        }
        get("/api/version") {
            call.respondText("""{"server":"$SERVER_ID"}""", ContentType.Application.Json)
        }
        get("/api/keybindings") {
            val player = call.request.queryParameters["player"]
            val bindings =
                if (player != null && persistence != null) {
                    persistence.loadPlayerKeyBindings(player)
                } else {
                    loadKeyBindings(Path.of(dataPath + "/config/keybindings.yaml"))
                }
            val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
            call.respondText(
                Json.encodeToString(serializer, bindings), ContentType.Application.Json)
        }
        get("/api/autocomplete/{commandId}/{argIndex}") {
            val commandId =
                call.parameters["commandId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val argIndex =
                call.parameters["argIndex"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
            val partial = call.request.queryParameters["partial"] ?: ""
            val player = call.request.queryParameters["player"] ?: ""
            val results = gameLoop.autocomplete(commandId, argIndex, partial, player)
            call.respondText(
                Json.encodeToString(ListSerializer(String.serializer()), results),
                ContentType.Application.Json)
        }
        get("/api/i18n/{locale}") {
            val locale = call.parameters["locale"] ?: "en"
            val keys = gameLoop.i18n.clientKeys(locale)
            val serializer = MapSerializer(String.serializer(), String.serializer())
            call.respondText(Json.encodeToString(serializer, keys), ContentType.Application.Json)
        }
        get("/api/layout/registry") {
            call.respondText(
                Json.encodeToString(
                    ListSerializer(WidgetRegistryEntry.serializer()), WIDGET_REGISTRY),
                ContentType.Application.Json)
        }
        get("/api/items/meta") {
            val serializer =
                MapSerializer(
                    String.serializer(), MapSerializer(String.serializer(), String.serializer()))
            val meta =
                ItemRegistry.keys().associate { type ->
                    val def = ItemRegistry.get(type)
                    type.id to mapOf("label" to def.label, "bg" to def.bg)
                }
            call.respondText(Json.encodeToString(serializer, meta), ContentType.Application.Json)
        }
        get("/api/attacks") {
            val serializer =
                MapSerializer(
                    String.serializer(), MapSerializer(String.serializer(), String.serializer()))
            val meta =
                gameLoop.attackRegistry.mapValues { (_, def) ->
                    mapOf(
                        "damageType" to def.damageType.name,
                        "manaCost" to def.manaCost.toString(),
                        "rageCost" to def.rageCost.toString(),
                        "cooldownMs" to def.cooldownMs.toString(),
                    )
                }
            call.respondText(Json.encodeToString(serializer, meta), ContentType.Application.Json)
        }
        get("/api/biomes") {
            val colors =
                biomeRegistry.biomes.associate { b ->
                    b.id to (b.grassColor ?: listOf(0.47, 0.75, 0.35))
                }
            val serializer = MapSerializer(String.serializer(), ListSerializer(Double.serializer()))
            call.respondText(Json.encodeToString(serializer, colors), ContentType.Application.Json)
        }
        get("/api/player/{name}/skin") {
            val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val state =
                persistence?.loadPlayerState(name)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondText("""{"skin":"${state.skin}"}""", ContentType.Application.Json)
        }
        put("/api/player/{name}/skin") {
            val name = call.parameters["name"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val body = call.receiveText()
            val skin =
                Regex(""""skin"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                    ?: return@put call.respond(HttpStatusCode.BadRequest)
            val available = availablePlayerSkins()
            if (skin !in available) return@put call.respond(HttpStatusCode.BadRequest)
            val p = persistence ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
            val existing = p.loadPlayerState(name)
            val state =
                existing?.copy(skin = skin)
                    ?: PlayerState(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        pos = Vec3(SPAWN_X, SPAWN_Y, SPAWN_Z),
                        orientation = Orientation(0f, 0f),
                        skin = skin,
                        rpgOptOut = false,
                    )
            p.savePlayerState(name, state)
            call.respondText("""{"skin":"$skin"}""", ContentType.Application.Json)
        }
        get("/api/player/{name}/armors") {
            val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val armors = persistence?.loadPlayerState(name)?.armors ?: emptyList()
            call.respondText(
                Json.encodeToString(ListSerializer(String.serializer()), armors),
                ContentType.Application.Json)
        }
        get("/api/player/{name}/rpg") {
            val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val characterData =
                persistence?.loadPlayerState(name)?.characterData
                    ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondText(
                """{"characterClass":"${characterData.characterClass}"}""",
                ContentType.Application.Json)
        }
        get("/api/skins") {
            val skins = availablePlayerSkins()
            call.respondText(
                Json.encodeToString(ListSerializer(String.serializer()), skins),
                ContentType.Application.Json)
        }
        get("/api/armors") {
            val armors =
                ArmorRegistryLoader(
                        Path.of("resources/armors"), Path.of("$dataPath/resources/armors"))
                    .load()
            call.respondText(
                Json.encodeToString(
                    MapSerializer(String.serializer(), ArmorDefinition.serializer()), armors),
                ContentType.Application.Json)
        }
        staticFiles("/api/models", java.io.File("resources"))
        mapRoutes(gameLoop)
        metricsRoutes(gameLoop)
        chunkRoutes(world, tokenStore, serverConfig.chunks.httpWorkers)
        webSocket("/game") { gameLoop.onConnect(this) }
        webSocket("/chunks") { gameLoop.onChunkConnect(this) }
    }
}
