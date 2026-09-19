package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.minigame.MiniGameDefinition
import org.micoli.micraft.game.minigame.MiniGameRegistry

class MiniGamesController(private val miniGameRegistry: MiniGameRegistry) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/minigames",
                {
                    description = "List all registered mini-games (id, display name, bundle URL)"
                    response { code(HttpStatusCode.OK) { body<List<MiniGameDefinition>>() } }
                }) {
                    call.respondText(
                        Json.encodeToString(
                            ListSerializer(MiniGameDefinition.serializer()),
                            miniGameRegistry.all()),
                        ContentType.Application.Json)
                }
        }
}
