package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AssetNotifyController(private val manifestController: AssetManifestController) {
    private val sessions = ConcurrentHashMap<String, DefaultWebSocketSession>()

    /** Start the background worker that publishes the current asset signature to all clients. */
    fun start(application: Application) {
        application.launch {
            while (true) {
                delay(5_000)
                publishVersion()
            }
        }
    }

    /** Broadcast the current asset signature; the service worker compares it to its own. */
    private suspend fun publishVersion() {
        val sig = manifestController.signature() ?: return
        broadcast("""{"type":"version","data":"$sig"}""")
    }

    /** Force all clients to refresh regardless of their current version. */
    private suspend fun notifyAll() {
        manifestController.invalidateCache()
        broadcast("""{"type":"reload"}""")
    }

    private suspend fun broadcast(message: String) {
        sessions.values.forEach { session -> runCatching { session.send(message) } }
    }

    fun register(route: Route) =
        route.apply {
            webSocket("/ws") {
                val id = UUID.randomUUID().toString()
                sessions[id] = this
                runCatching {
                    manifestController.signature()?.let { sig ->
                        send("""{"type":"version","data":"$sig"}""")
                    }
                }
                try {
                    for (frame in incoming) {
                        // Inbound frames are ignored; the client compares versions itself.
                    }
                } finally {
                    sessions.remove(id)
                }
            }

            post("/api/assets/reload") {
                call.application.launch { notifyAll() }
                call.respond(HttpStatusCode.NoContent)
            }
        }
}
