package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.GameLoop

class AttacksController(private val gameLoop: GameLoop) {
    fun register(route: Route) =
        route.apply {
            get("/api/attacks") {
                val serializer =
                    MapSerializer(
                        String.serializer(),
                        MapSerializer(String.serializer(), String.serializer()))
                val meta =
                    gameLoop.attackRegistry.mapValues { (_, def) ->
                        mapOf(
                            "damageType" to def.damageType.name,
                            "manaCost" to def.manaCost.toString(),
                            "rageCost" to def.rageCost.toString(),
                            "cooldownMs" to def.cooldownMs.toString(),
                        )
                    }
                call.respondText(
                    Json.encodeToString(serializer, meta), ContentType.Application.Json)
            }
        }
}
