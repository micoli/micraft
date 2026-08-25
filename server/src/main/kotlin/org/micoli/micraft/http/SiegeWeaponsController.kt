package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Path
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.placeable.siege.SiegeWeaponRegistryLoader
import org.micoli.micraft.placeable.siege.SiegeWeaponDefinition

// encodeDefaults=true — most weapons don't override every stat (e.g. CATAPULT leaves
// launchPitchDeg/impactRadius/pitchStepRange/powerStepRange at their YAML defaults), and the
// default Json instance omits properties equal to their default value, which the admin codex's
// siege weapons tab then rendered as "undefined".
private val json = Json { encodeDefaults = true }

class SiegeWeaponsController(private val dataPath: String) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/siege-weapons",
                {
                    description = "List all siege weapon definitions"
                    response {
                        code(HttpStatusCode.OK) { body<Map<String, SiegeWeaponDefinition>>() }
                    }
                }) {
                    val weapons =
                        SiegeWeaponRegistryLoader(
                                Path.of("resources/siege/weapons"),
                                Path.of("$dataPath/resources/siege/weapons"))
                            .load()
                            .mapKeys { it.key.id }
                    call.respondText(
                        json.encodeToString(
                            MapSerializer(String.serializer(), SiegeWeaponDefinition.serializer()),
                            weapons),
                        ContentType.Application.Json)
                }
        }
}
