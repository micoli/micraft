package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.vehicle.VehicleModelDefinition
import org.micoli.micraft.game.vehicle.VehicleModelRegistryLoader

class VehiclesController(private val dataPath: String = "data") {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/vehicles/{name}/config",
                {
                    description = "Vehicle model config (speed, seat offset) for a named vehicle"
                    request { pathParameter<String>("name") { description = "Vehicle model name" } }
                    response {
                        code(HttpStatusCode.OK) { body<VehicleModelDefinition>() }
                        code(HttpStatusCode.NotFound) { description = "Vehicle model not found" }
                    }
                }) {
                    val name = call.parameters["name"].orEmpty()
                    val definition =
                        if (name.isBlank() || name.contains('/') || name.contains("..")) null
                        else
                            VehicleModelRegistryLoader(
                                    Path.of("resources/vehicles"),
                                    Path.of("$dataPath/resources/vehicles"))
                                .load(name)
                    if (definition == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respondText(
                        Json.encodeToString(VehicleModelDefinition.serializer(), definition),
                        ContentType.Application.Json)
                }
        }
}
