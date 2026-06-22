package org.micoli.micraft

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import com.charleskorn.kaml.Yaml
import org.micoli.micraft.world.BiomeConfig
import org.micoli.micraft.world.BiomeRegistry
import org.micoli.micraft.world.loadKeyBindings
import org.micoli.micraft.world.ChunkGenerator
import org.slf4j.LoggerFactory
import org.micoli.micraft.world.DebugChunkGenerator
import org.micoli.micraft.world.ProceduralChunkGenerator
import org.micoli.micraft.world.WorldMetadata
import org.micoli.micraft.world.WorldPersistence
import org.micoli.micraft.world.WorldState
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("Application")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets) {
    }

    val debugWorld = System.getenv("MICRAFT_DEBUG_WORLD") == "1"
    val worldName = System.getenv("MICRAFT_WORLD_NAME")?.takeIf { it.isNotBlank() } ?: "default_world"

    val persistence = if (!debugWorld) {
        val dir = Path.of("data/world/$worldName")
        WorldPersistence(dir).also { p ->
            if (p.loadMetadata() == null) {
                p.saveMetadata(WorldMetadata(
                    seed = 42L,
                    generator = "procedural",
                    createdAt = Instant.now().toString(),
                ))
            }
        }
    } else null

    fun loadBiomeRegistry(): BiomeRegistry {
        val biomeFile = Path.of("data/biomes/biomes.yaml")
        return if (biomeFile.exists()) {
            log.info("Loading biomes from {}", biomeFile.toAbsolutePath())
            runCatching {
                val config = Yaml.default.decodeFromString(BiomeConfig.serializer(), biomeFile.readText())
                val registry = BiomeRegistry.from(config)
                log.info(
                    "Biomes loaded: [{}] | voronoiCellSize={} blendRadius={}",
                    config.biomes.joinToString { it.id },
                    config.voronoiCellSize,
                    config.voronoiBlendRadius,
                )
                registry
            }.getOrElse { e ->
                log.warn("Failed to load biomes.yaml ({}), using default", e.message)
                BiomeRegistry.default()
            }
        } else {
            log.warn("No biomes.yaml found at {} — using default (plains only)", biomeFile.toAbsolutePath())
            BiomeRegistry.default()
        }
    }

    val biomeRegistry = if (!debugWorld) loadBiomeRegistry() else {
        log.info("Debug world mode — biomes disabled")
        BiomeRegistry.default()
    }

    val generator = if (debugWorld) DebugChunkGenerator()
                    else ProceduralChunkGenerator(seed = 42L, biomeRegistry = biomeRegistry)
    log.info("World: {} | generator={} | seed=42", worldName, generator::class.simpleName)

    val reloadBiomes: (() -> ChunkGenerator)? = if (!debugWorld) {
        { ProceduralChunkGenerator(seed = 42L, biomeRegistry = loadBiomeRegistry()) }
    } else null

    val world = WorldState(generator = generator, persistence = persistence)
    val gameLoop = GameLoop(world, persistence, reloadBiomes)
    gameLoop.start(this)

    Runtime.getRuntime().addShutdownHook(Thread {
        gameLoop.shutdown()
    })

    routing {
        get("/") {
            call.respondRedirect("http://localhost:8081/", permanent = false)
        }
        get("/api/keybindings") {
            val bindings = loadKeyBindings(Path.of("data/personal/keybindings.yaml"))
            val serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
            call.respondText(Json.encodeToString(serializer, bindings), ContentType.Application.Json)
        }
        webSocket("/game") {
            gameLoop.onConnect(this)
        }
    }
}
