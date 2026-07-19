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

@Serializable data class FillerEntry(val type: BlockType, val density: Double = 1.0)

@Serializable
data class CavernConfig(
    val cavernMinHeight: Int = 5,
    val cavernMaxHeight: Int = 50,
    val stalactitesPresent: Boolean = false,
    val stalagmitesPresent: Boolean = false,
    val wallBlock: BlockType = BlockType.STONE,
    val numberPerVoronoi: Int = 1,
    val cavernMinRadius: Int = 20,
    val cavernMaxRadius: Int = 50,
    val staircaseEnabled: Boolean = false,
)

@Serializable
data class BiomeDefinition(
    val id: String,
    val zones: List<BiomeZone>,
    val surface: BlockType,
    val subsurface: BlockType,
    val subsurfaceDepth: Int = 3,
    val fillers: List<FillerEntry> = listOf(FillerEntry(BlockType.STONE, 1.0)),
    val vegetation: List<VegetationEntry> = emptyList(),
    val elevationMin: Int = 40,
    val elevationMax: Int = 120,
    val grassColor: List<Double>? = null,
    val waterSourceRate: Double = 0.0,
    val caverns: CavernConfig? = null,
    val maxNpcs: Int = 0,
) {
    fun selectFiller(hash: Double): BlockType {
        val total = fillers.sumOf { it.density }
        var cumulative = 0.0
        for (entry in fillers) {
            cumulative += entry.density / total
            if (hash < cumulative) return entry.type
        }
        return fillers.last().type
    }
}

@Serializable
data class BiomeConfig(
    val biomes: List<BiomeDefinition>,
    val voronoiCellSize: Int = 256,
    val voronoiBlendRadius: Int = 16,
)
