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
import org.micoli.micraft.game.rpg.CharacterConstants
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData

class CharacterController(private val persistence: WorldPersistence?) {
    fun register(route: Route) =
        route.apply {
            post("/api/character/create") {
                val body = call.receiveText()
                val playerName =
                    Regex(""""playerName"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                val skin =
                    Regex(""""skin"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "player"
                val email =
                    Regex(""""email"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: ""
                if (playerName.length !in 3..24) return@post call.respond(HttpStatusCode.BadRequest)
                val available = availablePlayerSkins()
                val safeSkin = if (skin in available) skin else available.firstOrNull() ?: "player"
                val p = persistence ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
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
            post("/api/character/rpgcreate") {
                val body = call.receiveText()
                val playerName =
                    Regex(""""playerName"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                if (playerName.length !in 3..24) return@post call.respond(HttpStatusCode.BadRequest)
                val skin =
                    Regex(""""skin"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: "player"
                val email =
                    Regex(""""email"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1) ?: ""
                val characterClassStr =
                    Regex(""""characterClass"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                val characterClass =
                    runCatching { CharacterClass.valueOf(characterClassStr.uppercase()) }
                        .getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                fun extractInt(field: String): Int? =
                    Regex(""""$field"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull()
                val str = extractInt("str") ?: return@post call.respond(HttpStatusCode.BadRequest)
                val dex = extractInt("dex") ?: return@post call.respond(HttpStatusCode.BadRequest)
                val intel =
                    extractInt("intel") ?: return@post call.respond(HttpStatusCode.BadRequest)
                val wis = extractInt("wis") ?: return@post call.respond(HttpStatusCode.BadRequest)
                val con = extractInt("con") ?: return@post call.respond(HttpStatusCode.BadRequest)
                val cha = extractInt("cha") ?: return@post call.respond(HttpStatusCode.BadRequest)
                val statValues = listOf(str, dex, intel, wis, con, cha)
                if (statValues.any {
                    it !in CharacterConstants.STAT_MIN_BUY..CharacterConstants.STAT_MAX_BUY
                })
                    return@post call.respond(HttpStatusCode.BadRequest)
                val totalCost = statValues.sumOf { CharacterConstants.POINT_BUY_COST[it] ?: 9 }
                if (totalCost > CharacterConstants.POINT_BUY_BUDGET)
                    return@post call.respond(HttpStatusCode.BadRequest)
                val p = persistence ?: return@post call.respond(HttpStatusCode.ServiceUnavailable)
                val existing = p.loadPlayerState(playerName)
                if (existing?.characterData != null)
                    return@post call.respond(HttpStatusCode.Conflict)
                val finalStats =
                    BaseStats(
                        str =
                            (str + characterClass.strBonus).coerceIn(
                                1, CharacterConstants.STAT_MAX_TOTAL),
                        dex =
                            (dex + characterClass.dexBonus).coerceIn(
                                1, CharacterConstants.STAT_MAX_TOTAL),
                        intel =
                            (intel + characterClass.intelBonus).coerceIn(
                                1, CharacterConstants.STAT_MAX_TOTAL),
                        wis =
                            (wis + characterClass.wisBonus).coerceIn(
                                1, CharacterConstants.STAT_MAX_TOTAL),
                        con =
                            (con + characterClass.conBonus).coerceIn(
                                1, CharacterConstants.STAT_MAX_TOTAL),
                        cha =
                            (cha + characterClass.chaBonus).coerceIn(
                                1, CharacterConstants.STAT_MAX_TOTAL),
                    )
                val prelimChar =
                    CharacterData(
                        id = UUID.randomUUID().toString(),
                        name = playerName,
                        characterClass = characterClass,
                        baseStats = finalStats,
                        currentHp = 0,
                        currentMana = 0,
                    )
                val derived = DerivedStatsCalculator.compute(prelimChar)
                val character =
                    prelimChar.copy(currentHp = derived.maxHp, currentMana = derived.maxMana)
                val available = availablePlayerSkins()
                val safeSkin = if (skin in available) skin else available.firstOrNull() ?: "player"
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
