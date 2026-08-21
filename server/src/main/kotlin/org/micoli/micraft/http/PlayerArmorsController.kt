package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.world.WorldPersistence

class PlayerArmorsController(
    private val persistence: WorldPersistence?,
    private val sessionRegistry: SessionRegistry? = null,
) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/player/{id}/armors",
                {
                    description = "Armor names currently equipped by a player"
                    request { pathParameter<String>("id") { description = "Player id" } }
                    response { code(HttpStatusCode.OK) { body<List<String>>() } }
                }) {
                    val id =
                        call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    // Connected players carry a fresh random session id each reconnect (no-auth
                    // guests), so the id saved on disk from a prior session won't match — read the
                    // live in-memory state first, disk only as a fallback for offline players.
                    val armors =
                        sessionRegistry?.get(id)?.state?.armors
                            ?: persistence?.loadPlayerStateById(id)?.armors
                            ?: emptyList()
                    call.respondText(
                        Json.encodeToString(ListSerializer(String.serializer()), armors),
                        ContentType.Application.Json)
                }
        }
}
