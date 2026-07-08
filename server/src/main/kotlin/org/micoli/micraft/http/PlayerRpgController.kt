package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.micoli.micraft.game.world.WorldPersistence

class PlayerRpgController(private val persistence: WorldPersistence?) {
    fun register(route: Route) =
        route.apply {
            get("/api/player/{name}/rpg") {
                val name =
                    call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val characterData =
                    persistence?.loadPlayerState(name)?.characterData
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respondText(
                    """{"characterClass":"${characterData.characterClass}"}""",
                    ContentType.Application.Json)
            }
        }
}
