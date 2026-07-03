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
                biomes =
                    listOf(
                        BiomeDefinition(
                            id = "snow_peaks",
                            zones = listOf(BiomeZone(0.0, 1.0, altitudeMin = 150, altitudeMax = 1024)),
                            surface = BlockType.SNOW,
                            subsurface = BlockType.STONE,
                            filler = BlockType.STONE,
                            subsurfaceDepth = 2,
                            elevationMin = 150,
                            elevationMax = 200,
                            vegetation = listOf(VegetationEntry(VegetationType.PINE_TREE_SNOW, 0.04)),
                        ),
                        BiomeDefinition(
                            id = "desert",
                            zones = listOf(BiomeZone(0.0, 0.12)),
                            surface = BlockType.SAND,
                            subsurface = BlockType.SANDSTONE,
                            filler = BlockType.STONE,
                            subsurfaceDepth = 4,
                            elevationMin = 40,
                            elevationMax = 70,
                        ),
                        BiomeDefinition(
                            id = "dry_plains",
                            zones = listOf(BiomeZone(0.12, 0.30)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            filler = BlockType.STONE,
                            elevationMin = 55,
                            elevationMax = 90,
                            vegetation = listOf(VegetationEntry(VegetationType.WEED, 0.08)),
                        ),
                        BiomeDefinition(
                            id = "plains",
                            zones = listOf(BiomeZone(0.30, 0.62)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            filler = BlockType.STONE,
                            elevationMin = 60,
                            elevationMax = 100,
                            vegetation =
                                listOf(
                                    VegetationEntry(VegetationType.FLOWER, 0.06),
                                    VegetationEntry(VegetationType.WEED, 0.05),
                                    VegetationEntry(VegetationType.OAK_TREE, 0.01),
                                ),
                        ),
                        BiomeDefinition(
                            id = "forest",
                            zones = listOf(BiomeZone(0.62, 0.82)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            filler = BlockType.STONE,
                            elevationMin = 60,
                            elevationMax = 110,
                            grassColor = listOf(0.3, 0.55, 0.2),
                            vegetation =
                                listOf(
                                    VegetationEntry(VegetationType.OAK_TREE, 0.12),
                                    VegetationEntry(VegetationType.FLOWER, 0.04),
                                    VegetationEntry(VegetationType.WEED, 0.04),
                                ),
                        ),
                        BiomeDefinition(
                            id = "pine_forest",
                            zones = listOf(BiomeZone(0.82, 1.0)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            filler = BlockType.STONE,
                            elevationMin = 70,
                            elevationMax = 130,
                            grassColor = listOf(0.25, 0.45, 0.22),
                            vegetation =
                                listOf(
                                    VegetationEntry(VegetationType.PINE_TREE, 0.15),
                                    VegetationEntry(VegetationType.WEED, 0.03),
                                ),
                        ),
                    ),
                voronoiCellSize = 256,
                voronoiBlendRadius = 20,
            )
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
