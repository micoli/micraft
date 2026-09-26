package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.micoli.micraft.SERVER_BUILD_TIMESTAMP
import org.micoli.micraft.protocol.PROTOCOL_FINGERPRINT

/** [protocolFingerprint]: the client reloads when its own compiled fingerprint differs. */
@Serializable data class ServerInfo(val buildTimestamp: String, val protocolFingerprint: String)

class ServerInfoController {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/server/info",
                {
                    description = "Server build timestamp and protocol fingerprint"
                    response { code(HttpStatusCode.OK) { body<ServerInfo>() } }
                }) {
                    call.respondText(
                        Json.encodeToString(
                            ServerInfo.serializer(),
                            ServerInfo(SERVER_BUILD_TIMESTAMP, PROTOCOL_FINGERPRINT)),
                        ContentType.Application.Json,
                    )
                }
        }
}
