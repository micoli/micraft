package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.player.Hand

@Serializable
data class PlayerHands(
    val dominantHand: Hand,
    val rightHandItem: String?,
    val leftHandItem: String?,
)

class PlayerHandsController(
    private val persistence: WorldPersistence?,
    private val sessionRegistry: SessionRegistry? = null,
) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/player/{id}/hands",
                {
                    description = "Wielded weapon/tool names and dominant hand for a player"
                    request { pathParameter<String>("id") { description = "Player id" } }
                    response { code(HttpStatusCode.OK) { body<PlayerHands>() } }
                }) {
                    val id =
                        call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    // Connected players carry a fresh random session id each reconnect (no-auth
                    // guests), so the id saved on disk from a prior session won't match — read the
                    // live in-memory state first, disk only as a fallback for offline players.
                    val state =
                        sessionRegistry?.get(id)?.state ?: persistence?.loadPlayerStateById(id)
                    val hands =
                        PlayerHands(
                            dominantHand = state?.dominantHand ?: Hand.RIGHT,
                            rightHandItem = state?.rightHandItem,
                            leftHandItem = state?.leftHandItem,
                        )
                    call.respondText(
                        Json.encodeToString(PlayerHands.serializer(), hands),
                        ContentType.Application.Json)
                }
        }
}
