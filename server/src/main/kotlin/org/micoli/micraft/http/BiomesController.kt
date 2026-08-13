package org.micoli.micraft.http

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.micoli.micraft.game.world.biome.BiomeRegistry

class BiomesController(private val biomeRegistry: BiomeRegistry) {
    fun register(route: Route) =
        route.apply {
            get(
                "/api/biomes",
                {
                    description = "Grass color per biome id, as [r, g, b] in 0..1"
                    response { code(HttpStatusCode.OK) { body<Map<String, List<Double>>>() } }
                }) {
                    val colors =
                        biomeRegistry.biomes.associate { b ->
                            b.id to (b.grassColor ?: listOf(0.47, 0.75, 0.35))
                        }
                    val serializer =
                        MapSerializer(String.serializer(), ListSerializer(Double.serializer()))
                    call.respondText(
                        Json.encodeToString(serializer, colors), ContentType.Application.Json)
                }
        }
}
