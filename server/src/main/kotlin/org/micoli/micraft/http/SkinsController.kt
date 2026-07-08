package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.command.commands.availablePlayerSkins

class SkinsController {
    fun register(route: Route) =
        route.apply {
            get("/api/skins") {
                val skins = availablePlayerSkins()
                call.respondText(
                    Json.encodeToString(ListSerializer(String.serializer()), skins),
                    ContentType.Application.Json)
            }
        }
}
