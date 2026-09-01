package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID
import kotlinx.serialization.Serializable
import org.micoli.micraft.command.commands.availablePlayerSkins
import org.micoli.micraft.game.SPAWN_X
import org.micoli.micraft.game.SPAWN_Y
import org.micoli.micraft.game.SPAWN_Z
import org.micoli.micraft.game.rpg.character.RpgCharacterBuilder
import org.micoli.micraft.game.rpg.character.RpgCharacterResult
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.CharacterClass

@Serializable
private data class CreateCharacterRequest(
    val playerName: String,
    val skin: String? = null,
    val email: String? = null,
)

@Serializable
private data class CreateCharacterResponse(
    val playerName: String,
    val skin: String,
    val id: String
)

@Serializable
private data class CreateRpgCharacterRequest(
    val playerName: String,
    val characterClass: String,
    val str: Int,
    val dex: Int,
    val intel: Int,
    val wis: Int,
    val con: Int,
    val cha: Int,
    val skin: String? = null,
    val email: String? = null,
)

@Serializable
private data class CreateRpgCharacterResponse(
    val playerName: String,
    val characterClass: String,
    val id: String,
)

class CharacterController(private val persistence: WorldPersistence?) {
    fun register(route: Route) =
        route.apply {
            post(
                "/api/character/create",
                {
                    description = "Create a new (non-RPG) character"
                    request { body<CreateCharacterRequest>() }
                    response {
                        code(HttpStatusCode.OK) { body<CreateCharacterResponse>() }
                        code(HttpStatusCode.BadRequest) {
                            description = "Invalid or missing fields"
                        }
                        code(HttpStatusCode.ServiceUnavailable) {
                            description = "No persistence backend"
                        }
                    }
                }) {
                    val body = call.receiveText()
                    val playerName =
                        Regex(""""playerName"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val skin =
                        Regex(""""skin"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                            ?: "articulated"
                    val email =
                        Regex(""""email"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: ""
                    if (playerName.length !in 3..24)
                        return@post call.respond(HttpStatusCode.BadRequest)
                    val available = availablePlayerSkins()
                    val safeSkin =
                        if (skin in available) skin else available.firstOrNull() ?: "articulated"
                    val p =
                        persistence ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                    val existing = p.loadPlayerState(playerName)
                    val base =
                        existing?.copy(skin = safeSkin)
                            ?: PlayerState(
                                id = UUID.randomUUID().toString(),
                                name = playerName,
                                pos = Vec3(SPAWN_X, SPAWN_Y, SPAWN_Z),
                                orientation = Orientation(0f, 0f),
                                skin = safeSkin,
                                rpgOptOut = true,
                            )
                    val state =
                        if (email.isNotEmpty() && base.email.isEmpty()) base.copy(email = email)
                        else base
                    p.savePlayerState(playerName, state)
                    call.respondText(
                        """{"playerName":"$playerName","skin":"$safeSkin","id":"${state.id}"}""",
                        ContentType.Application.Json)
                }
            post(
                "/api/character/rpgcreate",
                {
                    description = "Create a new RPG character (point-buy base stats + class)"
                    request { body<CreateRpgCharacterRequest>() }
                    response {
                        code(HttpStatusCode.OK) { body<CreateRpgCharacterResponse>() }
                        code(HttpStatusCode.BadRequest) {
                            description = "Invalid fields or stat budget"
                        }
                        code(HttpStatusCode.Conflict) {
                            description = "RPG character already exists"
                        }
                        code(HttpStatusCode.ServiceUnavailable) {
                            description = "No persistence backend"
                        }
                    }
                }) {
                    val body = call.receiveText()
                    val playerName =
                        Regex(""""playerName"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    if (playerName.length !in 3..24)
                        return@post call.respond(HttpStatusCode.BadRequest)
                    val skin =
                        Regex(""""skin"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                            ?: "articulated"
                    val email =
                        Regex(""""email"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: ""
                    val characterClassStr =
                        Regex(""""characterClass"\s*:\s*"([^"]+)"""")
                            .find(body)
                            ?.groupValues
                            ?.get(1) ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val characterClass =
                        runCatching { CharacterClass.valueOf(characterClassStr.uppercase()) }
                            .getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                    fun extractInt(field: String): Int? =
                        Regex(""""$field"\s*:\s*(\d+)""")
                            .find(body)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                    val str =
                        extractInt("str") ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val dex =
                        extractInt("dex") ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val intel =
                        extractInt("intel") ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val wis =
                        extractInt("wis") ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val con =
                        extractInt("con") ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val cha =
                        extractInt("cha") ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val p =
                        persistence ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                    val existing = p.loadPlayerState(playerName)
                    if (existing?.characterData != null)
                        return@post call.respond(HttpStatusCode.Conflict)
                    val character =
                        when (val r =
                            RpgCharacterBuilder.build(
                                playerName, characterClass, str, dex, intel, wis, con, cha)) {
                            is RpgCharacterResult.Success -> r.character
                            is RpgCharacterResult.Failure ->
                                return@post call.respond(HttpStatusCode.BadRequest)
                        }
                    val available = availablePlayerSkins()
                    val safeSkin =
                        if (skin in available) skin else available.firstOrNull() ?: "articulated"
                    val base =
                        existing?.copy(skin = safeSkin, characterData = character)
                            ?: PlayerState(
                                id = UUID.randomUUID().toString(),
                                name = playerName,
                                pos = Vec3(SPAWN_X, SPAWN_Y, SPAWN_Z),
                                orientation = Orientation(0f, 0f),
                                skin = safeSkin,
                                rpgOptOut = false,
                                characterData = character,
                            )
                    val state =
                        if (email.isNotEmpty() && base.email.isEmpty()) base.copy(email = email)
                        else base
                    p.savePlayerState(playerName, state)
                    call.respondText(
                        """{"playerName":"$playerName","characterClass":"${characterClass.name}","id":"${state.id}"}""",
                        ContentType.Application.Json)
                }
        }
}
