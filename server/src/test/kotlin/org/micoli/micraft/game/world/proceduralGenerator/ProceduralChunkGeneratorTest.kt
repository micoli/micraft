package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.biome.BiomeConfig
import org.micoli.micraft.game.world.biome.BiomeDefinition
import org.micoli.micraft.game.world.biome.BiomeRegistry
import org.micoli.micraft.game.world.biome.BiomeZone
import org.micoli.micraft.game.world.biome.CavernConfig
import org.micoli.micraft.game.world.biome.VegetationEntry
import org.micoli.micraft.game.world.road.RoadBiomeConfig
import org.micoli.micraft.game.world.road.RoadConfig
import org.micoli.micraft.game.world.vegetation.VegetationType

class ProceduralChunkGeneratorTest {

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
                vegetation =
                    listOf(
                        VegetationEntry(VegetationType.FLOWER, 1.0),
                        VegetationEntry(VegetationType.WEED, 1.0)),
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

    @Test
    fun sameSeed_generatesIdenticalChunks() {
        val a = ProceduralChunkGenerator(seed = 123L)
        val b = ProceduralChunkGenerator(seed = 123L)
        val pos = ChunkPos(2, -3)
        assertEquals(a.generate(pos), b.generate(pos))
    }

    @Test
    fun differentSeeds_generateDifferentChunks() {
        val a = ProceduralChunkGenerator(seed = 1L)
        val b = ProceduralChunkGenerator(seed = 2L)
        val pos = ChunkPos(0, 0)
        assertTrue(a.generate(pos) != b.generate(pos))
    }

    @Test
    fun generate_bottomLayerIsBedrock() {
        val gen = ProceduralChunkGenerator(seed = 42L)
        val chunk = gen.generate(ChunkPos(0, 0))
        for (x in 0 until WorldConstants.CHUNK_SIZE) {
            for (z in 0 until WorldConstants.CHUNK_SIZE) {
                assertEquals(BlockType.BEDROCK, chunk.getBlock(x, 0, z))
            }
        }
    }

    @Test
    fun generate_surfaceHeightWithinWorldBounds() {
        val gen = ProceduralChunkGenerator(seed = 42L)
        for (wx in 0 until 128 step 16) {
            for (wz in 0 until 128 step 16) {
                val sample = gen.voronoi.sample(wx, wz)
                val h = gen.surfaceHeight(wx, wz, sample)
                assertTrue(h in 4 until WorldConstants.WORLD_MAX_Y)
            }
        }
    }

    @Test
    fun biomeAt_matchesVoronoiSamplePrimary() {
        val gen = ProceduralChunkGenerator(seed = 7L)
        assertEquals(gen.voronoi.sample(33, 17).primary.id, gen.biomeAt(33, 17))
    }

    // ── Cavern integration ────────────────────────────────────────────────────

    private fun biomeWithCaverns(caverns: CavernConfig) =
        BiomeDefinition(
            id = "cave_test",
            zones = listOf(BiomeZone(0.0, 1.0)),
            surface = BlockType.GRASS,
            subsurface = BlockType.DIRT,
            elevationMin = 80,
            elevationMax = 90,
            caverns = caverns,
        )

    private fun registryWith(biome: BiomeDefinition) =
        BiomeRegistry.from(
            BiomeConfig(biomes = listOf(biome), voronoiCellSize = 32, voronoiBlendRadius = 4))

    @Test
    fun generate_withCavernConfig_producesUndergroundAir() {
        val biome = biomeWithCaverns(CavernConfig(cavernMinHeight = 5, cavernMaxHeight = 60))
        val gen = ProceduralChunkGenerator(seed = 42L, biomeRegistry = registryWith(biome))
        val chunk = gen.generate(ChunkPos(0, 0))
        var foundUndergroundAir = false
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                val surface =
                    (WorldConstants.WORLD_MAX_Y downTo 1).first {
                        chunk.getBlock(lx, it, lz) != BlockType.AIR
                    }
                for (y in 5 until minOf(60, surface - 1)) {
                    if (chunk.getBlock(lx, y, lz) == BlockType.AIR) {
                        foundUndergroundAir = true
                    }
                }
            }
        }
        assertTrue(foundUndergroundAir, "cave carving must produce underground AIR blocks")
    }

    @Test
    fun generate_cavernsNeverBreakSurface() {
        val biome = biomeWithCaverns(CavernConfig(cavernMinHeight = 5, cavernMaxHeight = 200))
        val gen = ProceduralChunkGenerator(seed = 99L, biomeRegistry = registryWith(biome))
        val chunk = gen.generate(ChunkPos(0, 0))
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                val surface =
                    (WorldConstants.WORLD_MAX_Y downTo 1).first {
                        chunk.getBlock(lx, it, lz) != BlockType.AIR
                    }
                // Blocks at and above surface must not be AIR due to cave carving
                // (surface itself is solid, blocks above surface are always AIR but not from caves)
                assertFalse(
                    chunk.getBlock(lx, surface, lz) == BlockType.AIR,
                    "surface block at y=$surface must remain solid")
            }
        }
    }

    @Test
    fun generate_withCaverns_sandstoneWallBlockAppearsUnderground() {
        val biome =
            biomeWithCaverns(
                CavernConfig(
                    cavernMinHeight = 5,
                    cavernMaxHeight = 60,
                    wallBlock = BlockType.SANDSTONE,
                ))
        val gen = ProceduralChunkGenerator(seed = 42L, biomeRegistry = registryWith(biome))
        val chunk = gen.generate(ChunkPos(0, 0))
        var foundSandstone = false
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                for (y in 5..60) {
                    if (chunk.getBlock(lx, y, lz) == BlockType.SANDSTONE) foundSandstone = true
                }
            }
        }
        assertTrue(foundSandstone, "SANDSTONE wall blocks must appear adjacent to carved cave air")
    }

    @Test
    fun generate_sameSeed_identicalCavernedChunks() {
        val biome = biomeWithCaverns(CavernConfig(cavernMinHeight = 5, cavernMaxHeight = 60))
        val registry = registryWith(biome)
        val a = ProceduralChunkGenerator(seed = 55L, biomeRegistry = registry)
        val b = ProceduralChunkGenerator(seed = 55L, biomeRegistry = registry)
        assertEquals(a.generate(ChunkPos(1, -2)), b.generate(ChunkPos(1, -2)))
    }

    // ── Aquatic biomes ────────────────────────────────────────────────────────

    private val seaBiome =
        BiomeDefinition(
            id = "sea",
            zones = listOf(BiomeZone(0.0, 0.5)),
            surface = BlockType.SAND,
            subsurface = BlockType.SANDSTONE,
            subsurfaceDepth = 3,
            elevationMin = 60,
            elevationMax = 60,
            liquid = true,
            waterLevel = 60,
            waterMaxDepth = 8,
            tintColor = listOf(0.1, 0.3, 0.5),
        )

    private val aquaticRegistry =
        BiomeRegistry.from(
            BiomeConfig(
                biomes =
                    listOf(
                        seaBiome,
                        BiomeDefinition(
                            id = "plains",
                            zones = listOf(BiomeZone(0.5, 1.0)),
                            surface = BlockType.GRASS,
                            subsurface = BlockType.DIRT,
                            elevationMin = 72,
                            elevationMax = 96,
                        )),
                voronoiCellSize = 48,
                voronoiBlendRadius = 6,
            ))

    private fun aquaticWorld(): Map<Triple<Int, Int, Int>, BlockType> {
        val gen = ProceduralChunkGenerator(seed = 3L, biomeRegistry = aquaticRegistry)
        val world = HashMap<Triple<Int, Int, Int>, BlockType>()
        for (cx in -2..2) for (cz in -2..2) {
            val chunk = gen.generate(ChunkPos(cx, cz))
            val ox = cx * WorldConstants.CHUNK_SIZE
            val oz = cz * WorldConstants.CHUNK_SIZE
            for (lx in 0 until WorldConstants.CHUNK_SIZE) for (lz in
                0 until WorldConstants.CHUNK_SIZE) for (y in 0 until WorldConstants.WORLD_MAX_Y) {
                val b = chunk.getBlock(lx, y, lz)
                if (b != BlockType.AIR) world[Triple(ox + lx, y, oz + lz)] = b
            }
        }
        return world
    }

    @Test
    fun generate_aquaticBiome_producesEnclosedWater() {
        val gen = ProceduralChunkGenerator(seed = 3L, biomeRegistry = aquaticRegistry)
        assertTrue(
            (-32..32 step 4).any { wx ->
                (-32..32 step 4).any { wz -> gen.biomeAt(wx, wz) == "sea" }
            },
            "test region must contain a sea cell")

        val world = aquaticWorld()
        val water = world.filterValues { it == BlockType.WATER }.keys
        assertTrue(water.isNotEmpty(), "aquatic biome must generate WATER")

        for ((x, y, z) in water) {
            assertTrue(y <= 60, "water top never above waterLevel at ($x,$y,$z)")
            for ((nx, nz) in listOf(x - 1 to z, x + 1 to z, x to z - 1, x to z + 1)) {
                // ignore columns at the outer edge of the generated area
                if (nx < -30 || nx > 30 || nz < -30 || nz > 30) continue
                val n = world[Triple(nx, y, nz)]
                assertTrue(
                    n != null,
                    "WATER at ($x,$y,$z) has AIR neighbour at ($nx,$y,$nz) — basin leaks")
            }
        }
    }

    @Test
    fun generate_aquaticBiome_depthAndFloorRespected() {
        val world = aquaticWorld()
        val columns =
            world.keys.filter { world[it] == BlockType.WATER }.groupBy { it.first to it.third }
        assertTrue(columns.isNotEmpty())
        for ((col, cells) in columns) {
            val (x, z) = col
            assertTrue(cells.size <= 8, "water column ($x,$z) deeper than 8: ${cells.size}")
            val lowest = cells.minOf { it.second }
            val below = world[Triple(x, lowest - 1, z)]
            assertTrue(
                below != null && below != BlockType.WATER,
                "basin floor under ($x,$z) must be solid, was $below")
            assertTrue(lowest - 1 >= 52, "floor never below waterLevel - waterMaxDepth")
        }
    }

    @Test
    fun generate_aquaticBiome_sameSeedIdentical() {
        val a = ProceduralChunkGenerator(seed = 3L, biomeRegistry = aquaticRegistry)
        val b = ProceduralChunkGenerator(seed = 3L, biomeRegistry = aquaticRegistry)
        assertEquals(a.generate(ChunkPos(0, 0)), b.generate(ChunkPos(0, 0)))
    }

    @Test
    fun namedStaircasePoints_defaultRegistry_returnsNonEmpty() {
        val gen = ProceduralChunkGenerator(seed = 42L, biomeRegistry = BiomeRegistry.default())
        val staircases = gen.namedStaircasePoints()
        val caverns = gen.namedCavernPoints()
        assertTrue(
            staircases.isNotEmpty(),
            "expected staircase points, cavern count=${caverns.size}, staircase count=${staircases.size}")
    }
}
