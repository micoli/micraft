package org.micoli.micraft

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.micoli.micraft.world.BiomeConfig
import org.micoli.micraft.world.BiomeRegistry
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
        pingPeriod = 15.seconds
        timeout = 15.seconds
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

    val biomeRegistry = if (!debugWorld) {
        val biomeFile = Path.of("data/biomes/biomes.json")
        if (biomeFile.exists()) {
            log.info("Loading biomes from {}", biomeFile.toAbsolutePath())
            runCatching {
                val config = Json.decodeFromString<BiomeConfig>(biomeFile.readText())
                val registry = BiomeRegistry.from(config)
                log.info(
                    "Biomes loaded: [{}] | voronoiCellSize={} blendRadius={}",
                    config.biomes.joinToString { it.id },
                    config.voronoiCellSize,
                    config.voronoiBlendRadius,
                )
                registry
            }.getOrElse { e ->
                log.warn("Failed to load biomes.json ({}), using default", e.message)
                BiomeRegistry.default()
            }
        } else {
            log.warn("No biomes.json found at {} — using default (plains only)", biomeFile.toAbsolutePath())
            BiomeRegistry.default()
        }
    } else {
        log.info("Debug world mode — biomes disabled")
        BiomeRegistry.default()
    }

    val generator = if (debugWorld) DebugChunkGenerator()
                    else ProceduralChunkGenerator(seed = 42L, biomeRegistry = biomeRegistry)
    log.info("World: {} | generator={} | seed=42", worldName, generator::class.simpleName)

    val world = WorldState(generator = generator, persistence = persistence)
    val gameLoop = GameLoop(world, persistence)
    gameLoop.start(this)

    Runtime.getRuntime().addShutdownHook(Thread {
        gameLoop.shutdown()
    })

    routing {
        get("/") {
            call.respondRedirect("http://localhost:8081/", permanent = false)
        }
        webSocket("/game") {
            gameLoop.onConnect(this)
        }
    }
}
