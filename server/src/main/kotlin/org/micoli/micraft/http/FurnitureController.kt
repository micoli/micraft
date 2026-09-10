package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.placeable.furniture.FurnitureRegistryLoader
import org.micoli.micraft.placeable.furniture.FurnitureDefinition

// encodeDefaults=true — most furniture yaml files only set bbmodelFile and leave width/height/
// rotatable at their defaults; the default Json instance would omit those and the admin codex
// furniture tab would render them as "undefined". Mirrors SiegeWeaponsController.
private val json = Json { encodeDefaults = true }

class FurnitureController {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/furnitures",
                {
                    description = "List all furniture definitions"
                    response {
                        code(HttpStatusCode.OK) { body<Map<String, FurnitureDefinition>>() }
                    }
                }) {
                    val furnitures = FurnitureRegistryLoader().load().mapKeys { it.key.id }
                    call.respondText(
                        json.encodeToString(
                            MapSerializer(String.serializer(), FurnitureDefinition.serializer()),
                            furnitures),
                        ContentType.Application.Json)
                }
        }
}
