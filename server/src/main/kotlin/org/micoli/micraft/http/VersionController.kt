package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class VersionController(private val serverId: String) {
    fun register(route: Route) =
        route.apply {
            get("/api/version") {
                call.respondText("""{"server":"$serverId"}""", ContentType.Application.Json)
            }
        }
}
