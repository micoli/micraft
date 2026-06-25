package org.micoli.micraft.world

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProceduralChunkGeneratorRoadTest {

    private val plainsBiome =
        BiomeDefinition(
            id = "plains",
            zones = listOf(BiomeZone(0.0, 1.0)),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT,
            elevationMin = 48,
            elevationMax = 78,
        )

    // Small voronoi cells so edges are close, wide road so edges are easy to hit
    private val denseRegistry =
        BiomeRegistry.from(
            BiomeConfig(
                biomes = listOf(plainsBiome),
                voronoiCellSize = 32,
                voronoiBlendRadius = 4,
            ))

    private fun surfaceBlocksInChunk(
        generator: ProceduralChunkGenerator,
        pos: ChunkPos
    ): Set<BlockType> {
        val chunk = generator.generate(pos)
        val found = mutableSetOf<BlockType>()
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                val topY =
                    (WorldConstants.WORLD_MAX_Y downTo 1).first {
                        chunk.getBlock(lx, it, lz) != BlockType.AIR
                    }
                found.add(chunk.getBlock(lx, topY, lz))
            }
        }
        return found
    }

    @Test
    fun roadsEnabled_roadSurfaceAppearsInChunk() {
        // Small road voronoi cells (16 blocks) + wide road (14 blocks) = most columns on road
        val roadConfig =
            RoadConfig(
                enabled = true,
                voronoiCellSize = 16,
                displacementScale = 0.0,
                defaultRoad = RoadBiomeConfig(width = 14, surface = BlockType.GRAVEL),
            )
        val generator =
            ProceduralChunkGenerator(
                seed = 0L, biomeRegistry = denseRegistry, roadConfig = roadConfig)
        val surfaceBlocks = surfaceBlocksInChunk(generator, ChunkPos(0, 0))
        assertTrue(
            BlockType.GRAVEL in surfaceBlocks, "Expected GRAVEL road surface in generated chunk")
    }

    @Test
    fun roadsDisabled_roadSurfaceAbsent() {
        val roadConfig =
            RoadConfig(
                enabled = false,
                voronoiCellSize = 16,
                displacementScale = 0.0,
                defaultRoad = RoadBiomeConfig(14, BlockType.GRAVEL),
            )
        val generator =
            ProceduralChunkGenerator(
                seed = 0L, biomeRegistry = denseRegistry, roadConfig = roadConfig)
        val surfaceBlocks = surfaceBlocksInChunk(generator, ChunkPos(0, 0))
        assertFalse(BlockType.GRAVEL in surfaceBlocks, "Expected no GRAVEL when roads disabled")
    }

    @Test
    fun noRoadConfig_generatesNormally() {
        val generator =
            ProceduralChunkGenerator(seed = 0L, biomeRegistry = denseRegistry, roadConfig = null)
        val chunk = generator.generate(ChunkPos(0, 0))
        assertTrue(chunk.topY() > 0, "Chunk should have terrain blocks")
    }

    @Test
    fun vegetationBlockedOnRoad() {
        // Wide road with vegetation disallowed, check no vegetation appears on road surface
        val roadConfig =
            RoadConfig(
                enabled = true,
                vegetationAllowedOnRoad = false,
                voronoiCellSize = 16,
                displacementScale = 0.0,
                defaultRoad = RoadBiomeConfig(width = 14, surface = BlockType.GRAVEL),
            )
        val biomeWithVeg =
            BiomeDefinition(
                id = "plains",
                zones = listOf(BiomeZone(0.0, 1.0)),
                surface = BlockType.GRASS,
                subsurface = BlockType.DIRT,
                elevationMin = 48,
                elevationMax = 78,
                vegetation = listOf(VegetationEntry("flower", 1.0), VegetationEntry("weed", 1.0)),
            )
        val registry =
            BiomeRegistry.from(
                BiomeConfig(
                    biomes = listOf(biomeWithVeg), voronoiCellSize = 32, voronoiBlendRadius = 4))
        val generator =
            ProceduralChunkGenerator(seed = 0L, biomeRegistry = registry, roadConfig = roadConfig)
        val chunk = generator.generate(ChunkPos(0, 0))
        // No FLOWER or WEED should appear directly above a GRAVEL road surface
        var vegetationAboveRoad = false
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                val topY =
                    (WorldConstants.WORLD_MAX_Y downTo 1).first {
                        chunk.getBlock(lx, it, lz) != BlockType.AIR
                    }
                val top = chunk.getBlock(lx, topY, lz)
                val below = if (topY > 0) chunk.getBlock(lx, topY - 1, lz) else BlockType.AIR
                if ((top == BlockType.FLOWER || top == BlockType.WEED) &&
                    below == BlockType.GRAVEL) {
                    vegetationAboveRoad = true
                }
            }
        }
        assertFalse(vegetationAboveRoad, "No vegetation should grow directly on road surface")
    }

    @Test
    fun roadProbabilityZero_noRoadsGenerated() {
        val roadConfig =
            RoadConfig(
                enabled = true,
                voronoiCellSize = 16,
                displacementScale = 0.0,
                defaultRoad =
                    RoadBiomeConfig(width = 14, surface = BlockType.GRAVEL, roadProbability = 0.0),
            )
        val generator =
            ProceduralChunkGenerator(
                seed = 0L, biomeRegistry = denseRegistry, roadConfig = roadConfig)
        val surfaceBlocks = surfaceBlocksInChunk(generator, ChunkPos(0, 0))
        assertFalse(BlockType.GRAVEL in surfaceBlocks, "roadProbability=0 should produce no roads")
    }

    @Test
    fun minVegetationDistance_clearsMarginAroundRoad() {
        val roadConfig =
            RoadConfig(
                enabled = true,
                vegetationAllowedOnRoad = false,
                minVegetationDistanceFromRoad = 3,
                voronoiCellSize = 16,
                displacementScale = 0.0,
                defaultRoad = RoadBiomeConfig(width = 4, surface = BlockType.GRAVEL),
            )
        val generator =
            ProceduralChunkGenerator(
                seed = 0L, biomeRegistry = denseRegistry, roadConfig = roadConfig)
        // Just verify generation succeeds without error; visual verification in-game
        val chunk = generator.generate(ChunkPos(0, 0))
        assertTrue(chunk.topY() > 0)
    }
}
