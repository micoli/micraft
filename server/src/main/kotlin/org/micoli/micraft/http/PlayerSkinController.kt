package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID
import org.micoli.micraft.command.commands.availablePlayerSkins
import org.micoli.micraft.game.SPAWN_X
import org.micoli.micraft.game.SPAWN_Y
import org.micoli.micraft.game.SPAWN_Z
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3

class PlayerSkinController(private val persistence: WorldPersistence?) {
    fun register(route: Route) =
        route.apply {
            get("/api/player/{name}/skin") {
                val name =
                    call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val state =
                    persistence?.loadPlayerState(name)
                        ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respondText("""{"skin":"${state.skin}"}""", ContentType.Application.Json)
            }
            put("/api/player/{name}/skin") {
                val name =
                    call.parameters["name"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                val body = call.receiveText()
                val skin =
                    Regex(""""skin"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                        ?: return@put call.respond(HttpStatusCode.BadRequest)
                val available = availablePlayerSkins()
                if (skin !in available) return@put call.respond(HttpStatusCode.BadRequest)
                val p = persistence ?: return@put call.respond(HttpStatusCode.ServiceUnavailable)
                val existing = p.loadPlayerState(name)
                val state =
                    existing?.copy(skin = skin)
                        ?: PlayerState(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            pos = Vec3(SPAWN_X, SPAWN_Y, SPAWN_Z),
                            orientation = Orientation(0f, 0f),
                            skin = skin,
                            rpgOptOut = false,
                        )
                p.savePlayerState(name, state)
                call.respondText("""{"skin":"$skin"}""", ContentType.Application.Json)
            }
        }
}
