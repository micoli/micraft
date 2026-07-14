package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.world.WorldPersistence

class PlayerArmorsController(private val persistence: WorldPersistence?) {
    fun register(route: Route) =
        route.apply {
            get("/api/player/{id}/armors") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val armors = persistence?.loadPlayerStateById(id)?.armors ?: emptyList()
                call.respondText(
                    Json.encodeToString(ListSerializer(String.serializer()), armors),
                    ContentType.Application.Json)
            }
        }
}
