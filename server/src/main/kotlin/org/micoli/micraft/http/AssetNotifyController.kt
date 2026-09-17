package org.micoli.micraft.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.send
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
                // Sent unconditionally (unlike the version frame below, which needs a resolvable
                // asset dir) so callers have a registration-confirmed signal to wait on before
                // triggering a reload — otherwise a POST /api/assets/reload racing this
                // registration can broadcast before this session is in `sessions` and be missed.
                runCatching { send("""{"type":"ready"}""") }
                runCatching {
                    manifestController.signature()?.let { sig ->
                        send("""{"type":"version","data":"$sig"}""")
                    }
                }
                try {
                    // Inbound frames are ignored; the client compares versions itself.
                    @Suppress("EmptyForBlock") for (ignored in incoming) {}
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
