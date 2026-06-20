package org.micoli.micraft

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.micoli.micraft.world.DebugChunkGenerator
import org.micoli.micraft.world.ProceduralChunkGenerator
import org.micoli.micraft.world.WorldState
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

    val world = WorldState(
        if (System.getenv("MICRAFT_DEBUG_WORLD") == "1") DebugChunkGenerator()
        else ProceduralChunkGenerator(seed = 42L)
    )
    val gameLoop = GameLoop(world)
    gameLoop.start(this)

    routing {
        get("/") {
            call.respondRedirect("http://localhost:8081/", permanent = false)
        }
        webSocket("/game") {
            gameLoop.onConnect(this)
        }
    }
}
