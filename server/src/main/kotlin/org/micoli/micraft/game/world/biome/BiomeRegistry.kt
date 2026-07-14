package org.micoli.micraft.game.world.biome

import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.vegetation.VegetationType

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
                            zones =
                                listOf(BiomeZone(0.0, 1.0, altitudeMin = 150, altitudeMax = 1024)),
                            surface = BlockType.SNOW,
                            subsurface = BlockType.STONE,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            subsurfaceDepth = 2,
                            elevationMin = 150,
                            elevationMax = 200,
                            vegetation =
                                listOf(VegetationEntry(VegetationType.PINE_TREE_SNOW, 0.04)),
                        ),
                        BiomeDefinition(
                            id = "desert",
                            zones = listOf(BiomeZone(0.0, 0.12)),
                            surface = BlockType.SAND,
                            subsurface = BlockType.SANDSTONE,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            subsurfaceDepth = 4,
                            elevationMin = 40,
                            elevationMax = 70,
                        ),
                        BiomeDefinition(
                            id = "dry_plains",
                            zones = listOf(BiomeZone(0.12, 0.30)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            elevationMin = 55,
                            elevationMax = 90,
                            vegetation = listOf(VegetationEntry(VegetationType.WEED, 0.08)),
                            caverns =
                                CavernConfig(
                                    cavernMinHeight = 5,
                                    cavernMaxHeight = 50,
                                    wallBlock = BlockType.STONE,
                                    numberPerVoronoi = 1,
                                    cavernMinRadius = 12,
                                    cavernMaxRadius = 30,
                                    staircaseEnabled = true,
                                ),
                        ),
                        BiomeDefinition(
                            id = "plains",
                            zones = listOf(BiomeZone(0.30, 0.62)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            elevationMin = 60,
                            elevationMax = 100,
                            vegetation =
                                listOf(
                                    VegetationEntry(VegetationType.FLOWER, 0.06),
                                    VegetationEntry(VegetationType.WEED, 0.05),
                                    VegetationEntry(VegetationType.OAK_TREE, 0.01),
                                ),
                            caverns =
                                CavernConfig(
                                    cavernMinHeight = 5,
                                    cavernMaxHeight = 55,
                                    wallBlock = BlockType.STONE,
                                    numberPerVoronoi = 2,
                                    cavernMinRadius = 15,
                                    cavernMaxRadius = 35,
                                    staircaseEnabled = true,
                                ),
                        ),
                        BiomeDefinition(
                            id = "forest",
                            zones = listOf(BiomeZone(0.62, 0.82)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            elevationMin = 60,
                            elevationMax = 110,
                            grassColor = listOf(0.3, 0.55, 0.2),
                            vegetation =
                                listOf(
                                    VegetationEntry(VegetationType.OAK_TREE, 0.12),
                                    VegetationEntry(VegetationType.FLOWER, 0.04),
                                    VegetationEntry(VegetationType.WEED, 0.04),
                                ),
                            caverns =
                                CavernConfig(
                                    cavernMinHeight = 5,
                                    cavernMaxHeight = 60,
                                    stalactitesPresent = true,
                                    stalagmitesPresent = true,
                                    wallBlock = BlockType.STONE,
                                    numberPerVoronoi = 3,
                                    cavernMinRadius = 20,
                                    cavernMaxRadius = 50,
                                    staircaseEnabled = true,
                                ),
                        ),
                        BiomeDefinition(
                            id = "pine_forest",
                            zones = listOf(BiomeZone(0.82, 1.0)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
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
