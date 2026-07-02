package org.micoli.micraft

import com.charleskorn.kaml.Yaml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
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
import org.micoli.micraft.world.BiomeConfig
import org.micoli.micraft.world.BiomeRegistry
import org.micoli.micraft.world.BlockRegistry
import org.micoli.micraft.world.BlockRegistryLoader
import org.micoli.micraft.world.ItemRegistry
import org.micoli.micraft.world.ItemRegistryLoader
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
import org.micoli.micraft.world.validateAllYamlConfigs
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Application")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

private val SERVER_ID: String = UUID.randomUUID().toString()

fun Application.module() {
    install(WebSockets) {}

    validateAllYamlConfigs()

    val serverConfig = loadServerConfig(java.nio.file.Path.of("data/config/server.yaml"))
    applyServerConfig(serverConfig)

    val gameConfig = loadGameConfig(java.nio.file.Path.of("data/config/game.yaml"))
    applyGameConfig(gameConfig)

    loadKeyBindings(java.nio.file.Path.of("data/config/keybindings.yaml"))

    val debugWorld = gameConfig.debugWorld
    val worldName =
        System.getenv("MICRAFT_WORLD_NAME")?.takeIf { it.isNotBlank() } ?: "default_world"

    val persistence =
        if (!debugWorld) {
            val dir = Path.of("data/world/$worldName")
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
            dataBlocksPath = Path.of("data/resources/blocks"),
            outputPath = Path.of("data/config/blocks.yaml"),
        )
    val itemRegistryLoader = ItemRegistryLoader(Path.of("data/config/items.yaml"))
    BlockRegistry.load(blockRegistryLoader.load())
    ItemRegistry.load(itemRegistryLoader.load())

    fun loadBiomeRegistry(): BiomeRegistry {
        val biomeFile = Path.of("data/config/biomes.yaml")
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

    val roadConfigPath = Path.of("data/config/roads.yaml")
    val roadConfig = if (!debugWorld) loadRoadConfig(roadConfigPath) else null

    val houseConfigPath = Path.of("data/config/houses.yaml")
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
        applyGameConfig(loadGameConfig(java.nio.file.Path.of("data/config/game.yaml")))
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
        get("/") { call.respondRedirect("http://localhost:8081/", permanent = false) }
        get("/api/version") {
            call.respondText("""{"server":"$SERVER_ID"}""", ContentType.Application.Json)
        }
        get("/api/keybindings") {
            val player = call.request.queryParameters["player"]
            val bindings =
                if (player != null && persistence != null) {
                    persistence.loadPlayerKeyBindings(player)
                } else {
                    loadKeyBindings(Path.of("data/config/keybindings.yaml"))
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
            val skin = persistence?.loadPlayerState(name)?.skin ?: "player"
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
        staticFiles("/api/models", java.io.File("resources"))
        mapRoutes(gameLoop)
        metricsRoutes(gameLoop)
        chunkRoutes(world, tokenStore, serverConfig.chunks.httpWorkers)
        webSocket("/game") { gameLoop.onConnect(this) }
        webSocket("/chunks") { gameLoop.onChunkConnect(this) }
    }
}
