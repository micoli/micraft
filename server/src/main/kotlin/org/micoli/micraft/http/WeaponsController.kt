package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.equipment.WeaponDefinition
import org.micoli.micraft.game.equipment.WeaponRegistryLoader

class WeaponsController {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/weapons",
                {
                    description = "List all weapon definitions"
                    response { code(HttpStatusCode.OK) { body<Map<String, WeaponDefinition>>() } }
                }) {
                    val weapons = WeaponRegistryLoader().load()
                    call.respondText(
                        Json.encodeToString(
                            MapSerializer(String.serializer(), WeaponDefinition.serializer()),
                            weapons),
                        ContentType.Application.Json)
                }
        }
}
