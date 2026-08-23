package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.world.WorldPersistence

@Serializable
data class PlayerOwnedEquipment(
    val armors: List<String>,
    val weapons: List<String>,
    val tools: List<String>,
)

class PlayerOwnedController(
    private val persistence: WorldPersistence?,
    private val sessionRegistry: SessionRegistry? = null,
) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/player/{id}/owned",
                {
                    description = "Armor/weapon/tool names owned by a player"
                    request { pathParameter<String>("id") { description = "Player id" } }
                    response { code(HttpStatusCode.OK) { body<PlayerOwnedEquipment>() } }
                }) {
                    val id =
                        call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    // Connected players carry a fresh random session id each reconnect (no-auth
                    // guests), so the id saved on disk from a prior session won't match — read the
                    // live in-memory state first, disk only as a fallback for offline players.
                    val state =
                        sessionRegistry?.get(id)?.state ?: persistence?.loadPlayerStateById(id)
                    val owned =
                        PlayerOwnedEquipment(
                            armors = state?.ownedArmors ?: emptyList(),
                            weapons = state?.ownedWeapons ?: emptyList(),
                            tools = state?.ownedTools ?: emptyList(),
                        )
                    call.respondText(
                        Json.encodeToString(PlayerOwnedEquipment.serializer(), owned),
                        ContentType.Application.Json)
                }
        }
}
