package org.micoli.micraft.game.world.biome

import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.vegetation.VegetationType

class BiomeRegistry(
    val biomes: List<BiomeDefinition>,
    val voronoiCellSize: Int = 256,
    val voronoiBlendRadius: Int = 16,
    val elevationBlendRadius: Int = 96,
    val zoneLevelSafeDist: Double = 768.0,
    val zoneLevelMaxDist: Double = 4096.0,
) {
    companion object {
        fun from(config: BiomeConfig) =
            BiomeRegistry(
                config.biomes,
                config.voronoiCellSize,
                config.voronoiBlendRadius,
                config.elevationBlendRadius,
                config.zoneLevelSafeDist,
                config.zoneLevelMaxDist,
            )

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
                            zones = listOf(BiomeZone(0.0, 0.35)),
                            surface = BlockType.SAND,
                            subsurface = BlockType.SANDSTONE,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            subsurfaceDepth = 4,
                            elevationMin = 50,
                            elevationMax = 70,
                        ),
                        BiomeDefinition(
                            id = "dry_plains",
                            zones = listOf(BiomeZone(0.35, 0.46)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.SANDSTONE,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            elevationMin = 56,
                            elevationMax = 82,
                            grassColor = listOf(0.62, 0.58, 0.3),
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
                            zones = listOf(BiomeZone(0.46, 0.56)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            elevationMin = 60,
                            elevationMax = 96,
                            grassColor = listOf(0.42, 0.66, 0.3),
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
                            zones = listOf(BiomeZone(0.56, 0.68)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            elevationMin = 64,
                            elevationMax = 110,
                            grassColor = listOf(0.3, 0.55, 0.2),
                            vegetation =
                                listOf(
                                    VegetationEntry(VegetationType.OAK_TREE, 0.045),
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
                            zones = listOf(BiomeZone(0.68, 1.0)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            elevationMin = 70,
                            elevationMax = 124,
                            grassColor = listOf(0.25, 0.45, 0.22),
                            vegetation =
                                listOf(
                                    VegetationEntry(VegetationType.PINE_TREE, 0.05),
                                    VegetationEntry(VegetationType.WEED, 0.03),
                                ),
                        ),
                    ),
                voronoiCellSize = 256,
                voronoiBlendRadius = 20,
                elevationBlendRadius = 96,
                zoneLevelSafeDist = 768.0,
                zoneLevelMaxDist = 4096.0,
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
