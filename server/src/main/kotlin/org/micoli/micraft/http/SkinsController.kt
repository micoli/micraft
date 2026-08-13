package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
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
            get(
                "/api/skins",
                {
                    description = "Names of all available player skins"
                    response { code(HttpStatusCode.OK) { body<List<String>>() } }
                }) {
                    val skins = availablePlayerSkins()
                    call.respondText(
                        Json.encodeToString(ListSerializer(String.serializer()), skins),
                        ContentType.Application.Json)
                }
            get(
                "/api/skins/{name}/config",
                {
                    description = "Skin config (eye offset, hidden bones) for a named skin"
                    request { pathParameter<String>("name") { description = "Skin name" } }
                    response {
                        code(HttpStatusCode.OK) { body<SkinDefinition>() }
                        code(HttpStatusCode.NotFound) { description = "Skin not found" }
                    }
                }) {
                    val name = call.parameters["name"].orEmpty()
                    val definition =
                        if (name.isBlank() || name.contains('/') || name.contains("..")) null
                        else
                            SkinRegistryLoader(
                                    Path.of("resources/skins"),
                                    Path.of("$dataPath/resources/skins"))
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
