package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.micoli.micraft.command.commands.availablePlayerSkins
import org.micoli.micraft.game.world.WorldPersistence

class PlayerSkinController(private val persistence: WorldPersistence?) {
    fun register(route: Route) =
        route.apply {
            get("/api/player/{id}/skin") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val state =
                    persistence?.loadPlayerStateById(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respondText("""{"skin":"${state.skin}"}""", ContentType.Application.Json)
            }
            put("/api/player/{id}/skin") {
                val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val skin =
                    Regex(""""skin"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                        ?: return@put call.respond(HttpStatusCode.BadRequest)
                val available = availablePlayerSkins()
                if (skin !in available) return@put call.respond(HttpStatusCode.BadRequest)
                val p = persistence ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                val existing =
                    p.loadPlayerStateById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
                val state = existing.copy(skin = skin)
                p.savePlayerState(existing.name, state)
                call.respondText("""{"skin":"$skin"}""", ContentType.Application.Json)
            }
        }
}
