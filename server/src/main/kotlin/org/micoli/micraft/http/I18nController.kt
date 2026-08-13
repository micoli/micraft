package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.GameLoop

class I18nController(private val gameLoop: GameLoop) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/i18n/{locale}",
                {
                    description = "Client-facing translation keys for a locale"
                    request {
                        pathParameter<String>("locale") { description = "Locale code, e.g. en/fr" }
                    }
                    response { code(HttpStatusCode.OK) { body<Map<String, String>>() } }
                }) {
                    val locale = call.parameters["locale"] ?: "en"
                    val keys = gameLoop.i18n.clientKeys(locale)
                    val serializer = MapSerializer(String.serializer(), String.serializer())
                    call.respondText(
                        Json.encodeToString(serializer, keys), ContentType.Application.Json)
                }
        }
}
