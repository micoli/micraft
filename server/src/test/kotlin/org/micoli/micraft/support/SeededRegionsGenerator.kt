package org.micoli.micraft.support

import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Region
import org.micoli.micraft.game.world.biome.BiomeDefinition
import org.micoli.micraft.game.world.biome.BiomeZone
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.FlatArenaChunkGenerator

/**
 * A flat grass floor split into plains Regions, one per seed: a position belongs to the Region of
 * the nearest seed, as in the real Voronoi layout.
 */
class SeededRegionsGenerator(
    seeds: List<Pair<Int, Int>>,
    regionBudget: Int = 10,
    dangerLevel: Int = 3,
    private val floor: ChunkGenerator =
        FlatArenaChunkGenerator(halfSize = 100_000, wallHeight = 0, vegetationDensity = 0.0),
) : ChunkGenerator by floor {
    private val biome =
        BiomeDefinition(
            id = "plains",
            zones = listOf(BiomeZone(moistureMin = 0.0, moistureMax = 1.0)),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT,
            regionBudget = regionBudget,
        )
    private val regions = seeds.map { (x, z) -> Region(x, z, biome, "Region${x}_$z", dangerLevel) }

    override fun biomeDefinitionAt(wx: Int, wz: Int) = biome

    override fun zoneLevelAt(wx: Int, wz: Int) = regions.first().dangerLevel

    override fun regionAt(wx: Int, wz: Int): Region = regions.minBy { distanceSq(it, wx, wz) }

    override fun regionsNear(wx: Int, wz: Int, radiusBlocks: Int): List<Region> =
        regions.filter { distanceSq(it, wx, wz) <= radiusBlocks.toLong() * radiusBlocks }

    private fun distanceSq(region: Region, wx: Int, wz: Int): Long {
        val dx = (region.seedX - wx).toLong()
        val dz = (region.seedZ - wz).toLong()
        return dx * dx + dz * dz
    }
}
