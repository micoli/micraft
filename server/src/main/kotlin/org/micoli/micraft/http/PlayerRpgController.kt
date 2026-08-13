package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.WorldPersistence

@Serializable private data class PlayerRpgResponse(val characterClass: String)

class PlayerRpgController(private val persistence: WorldPersistence?) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/player/{id}/rpg",
                {
                    description = "A player's RPG character class"
                    request { pathParameter<String>("id") { description = "Player id" } }
                    response {
                        code(HttpStatusCode.OK) { body<PlayerRpgResponse>() }
                        code(HttpStatusCode.NotFound) {
                            description = "Player or RPG character not found"
                        }
                    }
                }) {
                    val id =
                        call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val characterData =
                        persistence?.loadPlayerStateById(id)?.characterData
                            ?: return@get call.respond(HttpStatusCode.NotFound)
                    call.respondText(
                        """{"characterClass":"${characterData.characterClass}"}""",
                        ContentType.Application.Json)
                }
        }
}
