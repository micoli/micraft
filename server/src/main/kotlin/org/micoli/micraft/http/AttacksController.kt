package org.micoli.micraft.http

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
            get("/api/attacks") {
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
            get("/api/classes") {
                val classSer =
                    MapSerializer(
                        String.serializer(),
                        MapSerializer(
                            String.serializer(), ListSerializer(ClassAttackAccess.serializer())))
                val classes =
                    gameLoop.classRegistry.mapValues { (_, def) ->
                        def.levels.entries.associate { (level, entry) ->
                            level.toString() to entry.attacks
                        }
                    }
                call.respondText(
                    Json.encodeToString(classSer, classes), ContentType.Application.Json)
            }
        }
}
