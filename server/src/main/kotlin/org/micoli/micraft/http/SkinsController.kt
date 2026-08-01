package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.command.commands.availablePlayerSkins
import org.micoli.micraft.game.skin.SkinDefinition
import org.micoli.micraft.game.skin.SkinRegistryLoader

class SkinsController(private val dataPath: String = "data") {
    fun register(route: Route) =
        route.apply {
            get("/api/skins") {
                val skins = availablePlayerSkins()
                call.respondText(
                    Json.encodeToString(ListSerializer(String.serializer()), skins),
                    ContentType.Application.Json)
            }
            get("/api/skins/{name}/config") {
                val name = call.parameters["name"].orEmpty()
                val definition =
                    if (name.isBlank() || name.contains('/') || name.contains("..")) null
                    else
                        SkinRegistryLoader(
                                Path.of("resources/skins"), Path.of("$dataPath/resources/skins"))
                            .load(name)
                if (definition == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respondText(
                    Json.encodeToString(SkinDefinition.serializer(), definition),
                    ContentType.Application.Json)
            }
        }
}
