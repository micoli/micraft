package org.micoli.micraft

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.micoli.micraft.world.DebugChunkGenerator
import org.micoli.micraft.world.ProceduralChunkGenerator
import org.micoli.micraft.world.WorldMetadata
import org.micoli.micraft.world.WorldPersistence
import org.micoli.micraft.world.WorldState
import java.nio.file.Path
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

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

    val world = WorldState(
        generator = if (debugWorld) DebugChunkGenerator() else ProceduralChunkGenerator(seed = 42L),
        persistence = persistence,
    )
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
