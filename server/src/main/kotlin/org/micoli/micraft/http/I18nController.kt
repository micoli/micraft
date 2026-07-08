package org.micoli.micraft.http

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
            get("/api/i18n/{locale}") {
                val locale = call.parameters["locale"] ?: "en"
                val keys = gameLoop.i18n.clientKeys(locale)
                val serializer = MapSerializer(String.serializer(), String.serializer())
                call.respondText(
                    Json.encodeToString(serializer, keys), ContentType.Application.Json)
            }
        }
}
