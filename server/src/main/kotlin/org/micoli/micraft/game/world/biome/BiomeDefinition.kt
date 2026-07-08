package org.micoli.micraft.game.world.biome

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.vegetation.VegetationType

@Serializable
data class BiomeZone(
    val moistureMin: Double,
    val moistureMax: Double,
    val altitudeMin: Int = 0,
    val altitudeMax: Int = 0,
) {
    val altitudeConstrained: Boolean
        get() = altitudeMin != 0 || altitudeMax != 0
}

@Serializable data class VegetationEntry(val type: VegetationType, val density: Double = 0.0)

@Serializable
data class BiomeDefinition(
    val id: String,
    val zones: List<BiomeZone>,
    val surface: BlockType,
    val subsurface: BlockType,
    val subsurfaceDepth: Int = 3,
    val filler: BlockType = BlockType.STONE,
    val vegetation: List<VegetationEntry> = emptyList(),
    val elevationMin: Int = 40,
    val elevationMax: Int = 120,
    val grassColor: List<Double>? = null,
    val waterSourceRate: Double = 0.0,
)

@Serializable
data class BiomeConfig(
    val biomes: List<BiomeDefinition>,
    val voronoiCellSize: Int = 256,
    val voronoiBlendRadius: Int = 16,
)
