package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.GameLoop
import org.micoli.micraft.game.classes.ClassAttackAccess

class AttacksController(private val gameLoop: GameLoop) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/attacks",
                {
                    description = "Attack definitions, flattened by \"attackId:level\" key"
                    response {
                        code(HttpStatusCode.OK) { body<Map<String, Map<String, String>>>() }
                    }
                }) {
                    val serializer =
                        MapSerializer(
                            String.serializer(),
                            MapSerializer(String.serializer(), String.serializer()))
                    val flat = buildMap {
                        gameLoop.attackRegistry.forEach { (attackId, def) ->
                            def.levels.forEach { (level, levelDef) ->
                                put(
                                    "$attackId:$level",
                                    mapOf(
                                        "damageType" to def.damageType.name,
                                        "manaCost" to levelDef.manaCost.toString(),
                                        "rageCost" to levelDef.rageCost.toString(),
                                        "cooldownMs" to levelDef.cooldownMs.toString(),
                                        "power" to levelDef.power.toString(),
                                        "weaponDice" to levelDef.weaponDice,
                                        "attackId" to attackId,
                                        "level" to level.toString(),
                                    ))
                            }
                        }
                    }
                    call.respondText(
                        Json.encodeToString(serializer, flat), ContentType.Application.Json)
                }
            get(
                "/api/classes",
                {
                    description = "Attack ids accessible per RPG class, keyed by level"
                    response {
                        code(HttpStatusCode.OK) {
                            body<Map<String, Map<String, List<ClassAttackAccess>>>>()
                        }
                    }
                }) {
                    val classSer =
                        MapSerializer(
                            String.serializer(),
                            MapSerializer(
                                String.serializer(),
                                ListSerializer(ClassAttackAccess.serializer())))
                    val classes =
                        gameLoop.classRegistry.mapValues { (_, def) ->
                            def.levels.entries.associate { (level, entry) ->
                                level.toString() to entry.attacks
                            }
                        }
                    call.respondText(
                        Json.encodeToString(classSer, classes), ContentType.Application.Json)
                }
            get(
                "/api/spells",
                {
                    description = "Spell definitions, keyed by spell id"
                    response {
                        code(HttpStatusCode.OK) { body<Map<String, Map<String, String>>>() }
                    }
                }) {
                    val serializer =
                        MapSerializer(
                            String.serializer(),
                            MapSerializer(String.serializer(), String.serializer()))
                    val flat = buildMap {
                        gameLoop.spellRegistry.forEach { (spellId, def) ->
                            put(
                                spellId,
                                mapOf(
                                    "type" to def.type.name,
                                    "rageGain" to def.rageGain.toString(),
                                    "tokenCost" to def.tokenCost.toString(),
                                    "manaCost" to def.manaCost.toString(),
                                    "rageCost" to def.rageCost.toString(),
                                    "cooldownMs" to def.cooldownMs.toString(),
                                    "aoeRadius" to def.aoeRadius.toString(),
                                ))
                        }
                    }
                    call.respondText(
                        Json.encodeToString(serializer, flat), ContentType.Application.Json)
                }
        }
}
