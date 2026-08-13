package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
