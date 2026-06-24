package org.micoli.micraft.world

class BiomeRegistry(
    val biomes: List<BiomeDefinition>,
    val voronoiCellSize: Int = 256,
    val voronoiBlendRadius: Int = 16,
) {
    companion object {
        fun from(config: BiomeConfig) =
            BiomeRegistry(config.biomes, config.voronoiCellSize, config.voronoiBlendRadius)

        fun default() =
            BiomeRegistry(
                listOf(
                    BiomeDefinition(
                        "plains", listOf(BiomeZone(0.0, 1.0)), BlockType.GRASS, BlockType.DIRT)))
    }

    fun selectByMoisture(moisture: Double): BiomeDefinition =
        biomes.firstOrNull { b ->
            b.zones.any { z ->
                !z.altitudeConstrained && moisture >= z.moistureMin && moisture < z.moistureMax
            }
        } ?: biomes.first()

    fun altitudeOverride(surfaceY: Int, moisture: Double): BiomeDefinition? =
        biomes.firstOrNull { b ->
            b.zones.any { z ->
                z.altitudeConstrained &&
                    surfaceY in z.altitudeMin..z.altitudeMax &&
                    moisture >= z.moistureMin &&
                    moisture < z.moistureMax
            }
        }
}
