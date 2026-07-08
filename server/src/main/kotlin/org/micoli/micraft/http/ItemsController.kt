package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.world.ItemRegistry

class ItemsController {
    fun register(route: Route) =
        route.apply {
            get("/api/items/meta") {
                val serializer =
                    MapSerializer(
                        String.serializer(),
                        MapSerializer(String.serializer(), String.serializer()))
                val meta =
                    ItemRegistry.keys().associate { type ->
                        val def = ItemRegistry.get(type)
                        type.id to mapOf("label" to def.label, "bg" to def.bg)
                    }
                call.respondText(
                    Json.encodeToString(serializer, meta), ContentType.Application.Json)
            }
        }
}
