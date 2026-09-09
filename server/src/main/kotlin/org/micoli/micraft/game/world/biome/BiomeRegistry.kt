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
                            id = "sea",
                            zones = listOf(BiomeZone(0.0, 0.06)),
                            surface = BlockType.SAND,
                            subsurface = BlockType.SANDSTONE,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            subsurfaceDepth = 3,
                            elevationMin = 60,
                            elevationMax = 60,
                            grassColor = listOf(0.12, 0.3, 0.55),
                            liquid = true,
                            waterLevel = 60,
                            waterMaxDepth = 8,
                            waterFloorRelief = 4,
                            islandFraction = 0.08,
                            islandHeight = 6,
                            tintColor = listOf(0.09, 0.26, 0.5),
                        ),
                        BiomeDefinition(
                            id = "lake",
                            zones = listOf(BiomeZone(0.68, 0.74)),
                            surface = BlockType.GRAVEL,
                            subsurface = BlockType.DIRT,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            subsurfaceDepth = 3,
                            elevationMin = 72,
                            elevationMax = 72,
                            grassColor = listOf(0.22, 0.44, 0.62),
                            liquid = true,
                            waterLevel = 72,
                            waterMaxDepth = 8,
                            waterFloorRelief = 3,
                            islandFraction = 0.08,
                            islandHeight = 5,
                            tintColor = listOf(0.16, 0.38, 0.55),
                        ),
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
                            zones = listOf(BiomeZone(0.06, 0.35)),
                            surface = BlockType.SAND,
                            subsurface = BlockType.SANDSTONE,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            subsurfaceDepth = 4,
                            elevationMin = 72,
                            elevationMax = 92,
                        ),
                        BiomeDefinition(
                            id = "dry_plains",
                            zones = listOf(BiomeZone(0.35, 0.46)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.SANDSTONE,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            elevationMin = 72,
                            elevationMax = 98,
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
                            elevationMin = 72,
                            elevationMax = 108,
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
                            elevationMin = 72,
                            elevationMax = 118,
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
                            zones = listOf(BiomeZone(0.74, 1.0)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            fillers = listOf(FillerEntry(BlockType.STONE, 1.0)),
                            elevationMin = 72,
                            elevationMax = 126,
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
