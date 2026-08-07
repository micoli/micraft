package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.mail.MailManager

class PlayersController(private val mailManager: MailManager?) {
    fun register(route: Route) =
        route.apply {
            get("/api/players/names") {
                val names = mailManager?.knownPlayerNames() ?: emptyList()
                call.respondText(Json.encodeToString(names), ContentType.Application.Json)
            }
        }
}
