package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.micoli.micraft.SERVER_BUILD_TIMESTAMP

@Serializable data class ServerInfo(val buildTimestamp: String)

class ServerInfoController {
    fun register(route: Route) =
        route.apply {
            get("/api/server/info") {
                call.respondText(
                    Json.encodeToString(
                        ServerInfo.serializer(), ServerInfo(SERVER_BUILD_TIMESTAMP)),
                    ContentType.Application.Json,
                )
            }
        }
}
