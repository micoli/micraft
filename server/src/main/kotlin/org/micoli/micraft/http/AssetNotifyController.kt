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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AssetNotifyController(private val manifestController: AssetManifestController) {
    private data class ClientSession(
        val ws: DefaultWebSocketSession,
        @Volatile var clientSignature: String? = null,
    )

    private val sessions = ConcurrentHashMap<String, ClientSession>()

    /** Start the background worker that pushes reload to clients with stale asset versions. */
    fun start(application: Application) {
        application.launch {
            while (true) {
                delay(5_000)
                checkAndNotify()
            }
        }
    }

    private suspend fun checkAndNotify() {
        val currentSig = manifestController.signature() ?: return
        sessions.values.forEach { session ->
            val clientSig = session.clientSignature ?: return@forEach
            if (clientSig != currentSig) {
                runCatching { session.ws.send("""{"type":"reload"}""") }
            }
        }
    }

    private suspend fun notifyAll() {
        manifestController.invalidateCache()
        sessions.values.forEach { session ->
            runCatching { session.ws.send("""{"type":"reload"}""") }
        }
    }

    fun register(route: Route) =
        route.apply {
            webSocket("/ws") {
                val id = UUID.randomUUID().toString()
                sessions[id] = ClientSession(this)
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            runCatching {
                                val obj = Json.parseToJsonElement(frame.readText()).jsonObject
                                val type = obj["type"]?.jsonPrimitive?.content
                                if (type == "client-version") {
                                    sessions[id]?.clientSignature =
                                        obj["data"]?.jsonPrimitive?.content
                                }
                            }
                        }
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
