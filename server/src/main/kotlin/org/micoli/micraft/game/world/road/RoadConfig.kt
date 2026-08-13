package org.micoli.micraft.game.world.road

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
data class RoadBiomeConfig(
    val width: Int,
    val surface: BlockType,
    val roadProbability: Double = 1.0,
)

@Serializable
@JsonSchemaRoot(file = "roads.schema.json")
data class RoadConfig(
    val enabled: Boolean = true,
    val vegetationAllowedOnRoad: Boolean = false,
    val minVegetationDistanceFromRoad: Int = 1,
    val voronoiCellSize: Int = 128,
    val displacementScale: Double = 20.0,
    val displacementFrequency: Double = 0.02,
    val defaultRoad: RoadBiomeConfig = RoadBiomeConfig(3, BlockType.GRAVEL),
    val biomes: Map<String, RoadBiomeConfig> = emptyMap(),
) {
    fun configFor(biomeId: String) = biomes[biomeId] ?: defaultRoad

    fun surfaceFor(biomeId: String) = configFor(biomeId).surface
}
