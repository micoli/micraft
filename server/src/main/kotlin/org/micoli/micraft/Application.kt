package org.micoli.micraft

import com.charleskorn.kaml.Yaml
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
import java.time.Instant
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.auth.AuthProvider
import org.micoli.micraft.auth.LocalAuthProvider
import org.micoli.micraft.auth.OAuthProvider
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.auth.installAuthRoutes
import org.micoli.micraft.auth.loadGroupsConfig
import org.micoli.micraft.command.availablePlayerSkins
import org.micoli.micraft.http.chunkRoutes
import org.micoli.micraft.http.mapRoutes
import org.micoli.micraft.http.metricsRoutes
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.world.ArmorRegistryLoader
import org.micoli.micraft.world.BiomeConfig
import org.micoli.micraft.world.BiomeRegistry
import org.micoli.micraft.world.BlockRegistry
import org.micoli.micraft.world.BlockRegistryLoader
import org.micoli.micraft.world.ItemRegistry
import org.micoli.micraft.world.ItemRegistryLoader
import org.micoli.micraft.world.WearableSlots
import org.micoli.micraft.world.WorldMetadata
import org.micoli.micraft.world.WorldPersistence
import org.micoli.micraft.world.WorldState
import org.micoli.micraft.world.applyGameConfig
import org.micoli.micraft.world.applyServerConfig
import org.micoli.micraft.world.loadGameConfig
import org.micoli.micraft.world.loadHouseConfig
import org.micoli.micraft.world.loadKeyBindings
import org.micoli.micraft.world.loadRoadConfig
import org.micoli.micraft.world.loadServerConfig
import org.micoli.micraft.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.world.proceduralGenerator.chunkGenerator.DebugChunkGenerator
import org.micoli.micraft.world.validateAlli18nYamlConfigs
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

fun Application.module() {
    install(WebSockets) {}

    validateAlli18nYamlConfigs(configDir)
    val serverConfig = loadServerConfig(Path.of(dataPath + "/config/server.yaml"))
    applyServerConfig(serverConfig)

    val gameConfig = loadGameConfig(Path.of(dataPath + "/config/game.yaml"))
    applyGameConfig(gameConfig)

    loadKeyBindings(Path.of(dataPath + "/config/keybindings.yaml"))

    val debugWorld = gameConfig.debugWorld
    val worldName =
        System.getenv("MICRAFT_WORLD_NAME")?.takeIf { it.isNotBlank() } ?: "default_world"

    val persistence =
        if (!debugWorld) {
            val dir = Path.of(dataPath + "/world/$worldName")
            WorldPersistence(dir).also { p ->
                if (p.loadMetadata() == null) {
                    p.saveMetadata(
                        WorldMetadata(
                            seed = 42L,
                            generator = "procedural",
                            createdAt = Instant.now().toString(),
                        ))
                }
            }
        } else null

    val blockRegistryLoader =
        BlockRegistryLoader(
            resourcesBlocksPath = Path.of("resources/blocks"),
            dataBlocksPath = Path.of(dataPath + "/resources/blocks"),
            outputPath = Path.of(dataPath + "/config/blocks.yaml"),
        )

    val itemRegistryLoader = ItemRegistryLoader(Path.of(dataPath + "/config/items.yaml"))
    BlockRegistry.load(blockRegistryLoader.load())
    ItemRegistry.load(itemRegistryLoader.load())

    fun loadBiomeRegistry(): BiomeRegistry {
        validateYamlConfig(configDir.resolve("biomes.yaml"), "biomes.schema.json")
        val biomeFile = Path.of(dataPath + "/config/biomes.yaml")
        return if (biomeFile.exists()) {
            log.info("Loading biomes from {}", biomeFile.toAbsolutePath())
            runCatching {
                    val config =
                        Yaml.default.decodeFromString(
                            BiomeConfig.serializer(), biomeFile.readText())
                    val registry = BiomeRegistry.from(config)
                    log.info(
                        "Biomes loaded: [{}] | voronoiCellSize={} blendRadius={}",
                        config.biomes.joinToString { it.id },
                        config.voronoiCellSize,
                        config.voronoiBlendRadius,
                    )
                    registry
                }
                .getOrElse { e ->
                    log.warn("Failed to load biomes.yaml ({}), using default", e.message)
                    BiomeRegistry.default()
                }
        } else {
            log.warn(
                "No biomes.yaml found at {} — using default (plains only)",
                biomeFile.toAbsolutePath())
            BiomeRegistry.default()
        }
    }

    val biomeRegistry =
        if (!debugWorld) loadBiomeRegistry()
        else {
            log.info("Debug world mode — biomes disabled")
            BiomeRegistry.default()
        }

    validateYamlConfig(
        configDir.resolve("roads.yaml"),
        "roads.schema.json",
    )
    val roadConfigPath = Path.of(dataPath + "/config/roads.yaml")
    val roadConfig = if (!debugWorld) loadRoadConfig(roadConfigPath) else null

    val houseConfigPath = Path.of(dataPath + "/config/houses.yaml")
    val houseConfig = if (!debugWorld) loadHouseConfig(houseConfigPath) else null

    val generator =
        if (debugWorld) DebugChunkGenerator()
        else
            ProceduralChunkGenerator(
                seed = 42L,
                biomeRegistry = biomeRegistry,
                roadConfig = roadConfig,
                houseConfig = houseConfig,
            )
    log.info("World: {} | generator={} | seed=42", worldName, generator::class.simpleName)

    val reloadBiomes: (() -> ChunkGenerator)? =
        if (!debugWorld) {
            {
                ProceduralChunkGenerator(
                    seed = 42L,
                    biomeRegistry = loadBiomeRegistry(),
                    roadConfig = loadRoadConfig(roadConfigPath),
                    houseConfig = loadHouseConfig(houseConfigPath),
                )
            }
        } else null

    val reloadRegistries: () -> Unit = {
        BlockRegistry.load(blockRegistryLoader.reload())
        ItemRegistry.load(itemRegistryLoader.reload())
    }

    val reloadGameConfigLambda: () -> Unit = {
        validateYamlConfig(configDir.resolve("game.yaml"), "game.schema.json")
        applyGameConfig(loadGameConfig(Path.of(dataPath + "/config/game.yaml")))
    }

    val authConfig = serverConfig.auth
    val authScope = CoroutineScope(Dispatchers.Default)
    val groupsConfig = loadGroupsConfig(Path.of(authConfig.local.groupsFile))
    val (authProvider, tokenStore) =
        when (authConfig.provider) {
            "local" -> {
                val provider = LocalAuthProvider(Path.of(authConfig.local.usersFile), groupsConfig)
                Pair<AuthProvider, TokenStore>(provider, TokenStore(authScope))
            }
            "oauth" -> {
                val oauthCfg =
                    authConfig.oauth ?: error("auth.oauth config required when provider=oauth")
                Pair<AuthProvider, TokenStore>(
                    OAuthProvider(oauthCfg, groupsConfig), TokenStore(authScope))
            }
            else -> Pair<AuthProvider?, TokenStore?>(null, null)
        }

    val groupsFilePath = Path.of(authConfig.local.groupsFile)
    validateYamlConfig(groupsFilePath, "groups.schema.json")
    val reloadRbacLambda: (() -> Unit)? =
        when (val p = authProvider) {
            is LocalAuthProvider -> {
                { p.groupsConfig = loadGroupsConfig(groupsFilePath) }
            }
            is OAuthProvider -> {
                { p.groupsConfig = loadGroupsConfig(groupsFilePath) }
            }
            else -> null
        }

    val world = WorldState(generator = generator, persistence = persistence)
    val gameLoop =
        GameLoop(
            world,
            persistence,
            reloadBiomes,
            reloadRegistries = reloadRegistries,
            reloadGameConfig = reloadGameConfigLambda,
            tokenStore = tokenStore,
            authProvider = authProvider,
            groupsConfig = groupsConfig,
            reloadRbac = reloadRbacLambda,
            chunkSection = serverConfig.chunks,
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
        get("/api/skins") {
            val skins = availablePlayerSkins()
            call.respondText(
                Json.encodeToString(ListSerializer(String.serializer()), skins),
                ContentType.Application.Json)
        }
        get("/api/armors") {
            val armors = ArmorRegistryLoader(Path.of("resources/armors")).load()
            call.respondText(
                Json.encodeToString(
                    MapSerializer(String.serializer(), WearableSlots.serializer()), armors),
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
