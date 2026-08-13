package org.micoli.micraft.game.world.house

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
data class HouseTypeConfig(
    val id: String,
    val widthMin: Int,
    val widthMax: Int,
    val depthMin: Int,
    val depthMax: Int,
    val floorsMin: Int,
    val floorsMax: Int,
    val roofTypes: List<String>,
    val roomsMin: Int,
    val roomsMax: Int,
    val doorsMin: Int,
    val doorsMax: Int,
)

@Serializable
data class HouseBiomeConfig(
    val wallBlock: BlockType = BlockType.STONE,
    val roofBlock: BlockType = BlockType.STONE,
    val floorBlock: BlockType = BlockType.STONE,
    val houseProbability: Double = 0.0,
    val clusterBonus: Double = 0.0,
    val typeRates: Map<String, Double> = emptyMap(),
) {
    val allowedTypes: List<String>
        get() = typeRates.filter { it.value > 0 }.keys.toList()
}

@Serializable
@JsonSchemaRoot(file = "houses.schema.json")
data class HouseConfig(
    val enabled: Boolean = true,
    val gridCellSize: Int = 48,
    val clusterCheckRadius: Int = 2,
    val floorHeight: Int = 4,
    val maxHouseSize: Int = 20,
    val houseTypes: List<HouseTypeConfig> = emptyList(),
    val defaultBiome: HouseBiomeConfig = HouseBiomeConfig(),
    val biomes: Map<String, HouseBiomeConfig> = emptyMap(),
) {
    fun configFor(biomeId: String): HouseBiomeConfig = biomes[biomeId] ?: defaultBiome
}
