package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.mail.MailManager

class PlayersController(private val mailManager: MailManager?) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/players/names",
                {
                    description = "Names of all known players"
                    response { code(HttpStatusCode.OK) { body<List<String>>() } }
                }) {
                    val names = mailManager?.knownPlayerNames() ?: emptyList()
                    call.respondText(Json.encodeToString(names), ContentType.Application.Json)
                }
        }
}
