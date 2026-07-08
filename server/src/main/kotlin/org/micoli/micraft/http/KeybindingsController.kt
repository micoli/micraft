package org.micoli.micraft.http

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.keybinding.loadKeyBindings
import org.micoli.micraft.game.world.WorldPersistence

class KeybindingsController(
    private val persistence: WorldPersistence?,
    private val dataPath: String,
) {
    fun register(route: Route) =
        route.apply {
            get("/api/keybindings") {
                val player = call.request.queryParameters["player"]
                val bindings =
                    if (player != null && persistence != null) {
                        persistence.loadPlayerKeyBindings(player)
                    } else {
                        loadKeyBindings(Path.of(dataPath + "/config/keybindings.yaml"))
                    }
                val serializer =
                    MapSerializer(String.serializer(), ListSerializer(String.serializer()))
                call.respondText(
                    Json.encodeToString(serializer, bindings), ContentType.Application.Json)
            }
        }
}
