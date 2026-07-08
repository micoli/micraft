package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.GameLoop

class AutocompleteController(private val gameLoop: GameLoop) {
    fun register(route: Route) =
        route.apply {
            get("/api/autocomplete/{commandId}/{argIndex}") {
                val commandId =
                    call.parameters["commandId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                val argIndex =
                    call.parameters["argIndex"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                val partial = call.request.queryParameters["partial"] ?: ""
                val player = call.request.queryParameters["player"] ?: ""
                val results = gameLoop.autocomplete(commandId, argIndex, partial, player)
                call.respondText(
                    Json.encodeToString(ListSerializer(String.serializer()), results),
                    ContentType.Application.Json)
            }
        }
}
