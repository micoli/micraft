package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.equipment.ToolDefinition
import org.micoli.micraft.game.equipment.ToolRegistryLoader

class ToolsController(private val dataPath: String) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/tools",
                {
                    description = "List all tool definitions"
                    response { code(HttpStatusCode.OK) { body<Map<String, ToolDefinition>>() } }
                }) {
                    val tools =
                        ToolRegistryLoader(
                                Path.of("resources/tools"), Path.of("$dataPath/resources/tools"))
                            .load()
                    call.respondText(
                        Json.encodeToString(
                            MapSerializer(String.serializer(), ToolDefinition.serializer()), tools),
                        ContentType.Application.Json)
                }
        }
}
