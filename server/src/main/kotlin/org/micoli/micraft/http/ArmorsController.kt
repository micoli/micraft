package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.armor.ArmorRegistryLoader

class ArmorsController(private val dataPath: String) {
    fun register(route: Route) =
        route.apply {
            get("/api/armors") {
                val armors =
                    ArmorRegistryLoader(
                            Path.of("resources/armors"), Path.of("$dataPath/resources/armors"))
                        .load()
                call.respondText(
                    Json.encodeToString(
                        MapSerializer(String.serializer(), ArmorDefinition.serializer()), armors),
                    ContentType.Application.Json)
            }
        }
}
