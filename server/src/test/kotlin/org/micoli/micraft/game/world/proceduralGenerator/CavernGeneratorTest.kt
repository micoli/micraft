package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.biome.CavernConfig


class CavernGeneratorTest {

    private val airWire = BlockRegistry.wireIndex(BlockType.AIR).toByte()

    // Small cell size so the test chunk always contains multiple cells and seed points are nearby
    private fun gen(seed: Long = 42L) = CavernGenerator(seed, voronoiCellSize = 8)

    private fun solidChunk(): ByteArray {
        val blocks = ByteArray(Chunk.TOTAL)
        val bedrockWire = BlockRegistry.wireIndex(BlockType.BEDROCK).toByte()
        val stoneWire = BlockRegistry.wireIndex(BlockType.STONE).toByte()
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                for (y in 0 until Chunk.SIZE_Y) {
                    blocks[Chunk.index(lx, y, lz)] = if (y == 0) bedrockWire else stoneWire
                }
            }
        }
        return blocks
    }

    private fun basicConfig(
        minH: Int = 5,
        maxH: Int = 60,
        stalactites: Boolean = false,
        stalagmites: Boolean = false,
        wallBlock: BlockType = BlockType.STONE,
    ) =
        CavernConfig(
            cavernMinHeight = minH,
            cavernMaxHeight = maxH,
            stalactitesPresent = stalactites,
            stalagmitesPresent = stalagmites,
            wallBlock = wallBlock,
            cavernMinRadius = 8,
            cavernMaxRadius = 16,
        )

    // ── null config ───────────────────────────────────────────────────────────

    @Test
    fun carve_noCaverns_leavesChunkUnchanged() {
        val blocks = solidChunk()
        val original = blocks.copyOf()
        gen().carve(blocks, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> null })
        assertTrue(blocks.contentEquals(original))
    }

    // ── basic carving ──────────────────────────────────────────────────────────

    @Test
    fun carve_withConfig_producesAtLeastOneAirBlock() {
        val blocks = solidChunk()
        gen().carve(blocks, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> basicConfig() })
        assertTrue(blocks.count { it == airWire } > 0)
    }

    @Test
    fun carve_neverPunchesThroughSurface() {
        val config = basicConfig(minH = 5, maxH = 100)
        for (surfaceY in listOf(30, 60, 90)) {
            val blocks = solidChunk()
            gen()
                .carve(
                    blocks, 0, 0, surfaceAt = { _, _ -> surfaceY }, cavernsAt = { _, _ -> config })
            for (lx in 0 until WorldConstants.CHUNK_SIZE) {
                for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                    for (y in surfaceY until Chunk.SIZE_Y) {
                        assertFalse(
                            blocks[Chunk.index(lx, y, lz)] == airWire,
                            "cave carved through surface at y=$y (surfaceY=$surfaceY)")
                    }
                }
            }
        }
    }

    @Test
    fun carve_respectsHeightRange_noAirBelowMin() {
        val minH = 20
        val blocks = solidChunk()
        gen()
            .carve(
                blocks,
                0,
                0,
                surfaceAt = { _, _ -> 80 },
                cavernsAt = { _, _ -> basicConfig(minH = minH) })
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                for (y in 1 until minH) {
                    assertFalse(
                        blocks[Chunk.index(lx, y, lz)] == airWire,
                        "cave below cavernMinHeight=$minH at y=$y")
                }
            }
        }
    }

    @Test
    fun carve_y0AlwaysBedrockUnchanged() {
        val bedrockWire = BlockRegistry.wireIndex(BlockType.BEDROCK).toByte()
        val config = basicConfig(minH = 0)
        val blocks = solidChunk()
        gen().carve(blocks, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config })
        for (lx in 0 until WorldConstants.CHUNK_SIZE) {
            for (lz in 0 until WorldConstants.CHUNK_SIZE) {
                assertEquals(
                    bedrockWire, blocks[Chunk.index(lx, 0, lz)], "Y=0 bedrock must never be carved")
            }
        }
    }

    // ── determinism ────────────────────────────────────────────────────────────

    @Test
    fun carve_deterministic_sameSeedSameResult() {
        val config = basicConfig()
        val ba = solidChunk()
        val bb = solidChunk()
        gen(77L).carve(ba, 32, -16, surfaceAt = { _, _ -> 70 }, cavernsAt = { _, _ -> config })
        gen(77L).carve(bb, 32, -16, surfaceAt = { _, _ -> 70 }, cavernsAt = { _, _ -> config })
        assertTrue(ba.contentEquals(bb))
    }

    @Test
    fun carve_differentSeeds_differentResults() {
        val config = basicConfig()
        val ba = solidChunk()
        val bb = solidChunk()
        gen(1L).carve(ba, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config })
        gen(2L).carve(bb, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config })
        assertFalse(ba.contentEquals(bb))
    }

    // ── wall blocks ────────────────────────────────────────────────────────────

    @Test
    fun carve_wallBlocks_appliedAroundCaveAir() {
        val sandstoneWire = BlockRegistry.wireIndex(BlockType.SANDSTONE).toByte()
        val config = basicConfig(wallBlock = BlockType.SANDSTONE)
        val blocks = solidChunk()
        gen().carve(blocks, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config })
        assertTrue(blocks.any { it == sandstoneWire })
    }

    @Test
    fun carve_wallBlockAir_createsExtraPockets() {
        val config = basicConfig(wallBlock = BlockType.AIR)
        val blocks = solidChunk()
        val before = blocks.count { it == airWire }
        gen().carve(blocks, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config })
        assertTrue(blocks.count { it == airWire } > before)
    }

    // ── ornaments ──────────────────────────────────────────────────────────────

    @Test
    fun carve_stalactites_reduceAirCount() {
        val without = solidChunk()
        val with = solidChunk()
        gen()
            .carve(
                without,
                0,
                0,
                surfaceAt = { _, _ -> 80 },
                cavernsAt = { _, _ -> basicConfig(stalactites = false) })
        gen()
            .carve(
                with,
                0,
                0,
                surfaceAt = { _, _ -> 80 },
                cavernsAt = { _, _ -> basicConfig(stalactites = true) })
        assertTrue(with.count { it == airWire } < without.count { it == airWire })
    }

    @Test
    fun carve_stalagmites_reduceAirCount() {
        val without = solidChunk()
        val with = solidChunk()
        gen()
            .carve(
                without,
                0,
                0,
                surfaceAt = { _, _ -> 80 },
                cavernsAt = { _, _ -> basicConfig(stalagmites = false) })
        gen()
            .carve(
                with,
                0,
                0,
                surfaceAt = { _, _ -> 80 },
                cavernsAt = { _, _ -> basicConfig(stalagmites = true) })
        assertTrue(with.count { it == airWire } < without.count { it == airWire })
    }

    // ── seed points ────────────────────────────────────────────────────────────

    @Test
    fun cavernSeedPoints_count_matchesNumberPerVoronoi() {
        val g = CavernGenerator(42L, voronoiCellSize = 256)
        assertEquals(1, g.cavernSeedPoints(0, 0, basicConfig().copy(numberPerVoronoi = 1)).size)
        assertEquals(3, g.cavernSeedPoints(0, 0, basicConfig().copy(numberPerVoronoi = 3)).size)
    }

    @Test
    fun cavernSeedPoints_areDeterministic() {
        val g = CavernGenerator(7L, voronoiCellSize = 256)
        val config = basicConfig().copy(numberPerVoronoi = 2)
        assertEquals(g.cavernSeedPoints(3, -5, config), g.cavernSeedPoints(3, -5, config))
    }

    @Test
    fun cavernSeedPoints_differAcrossCells() {
        val g = CavernGenerator(7L, voronoiCellSize = 256)
        val config = basicConfig().copy(numberPerVoronoi = 1)
        assertFalse(g.cavernSeedPoints(0, 0, config) == g.cavernSeedPoints(1, 0, config))
    }

    @Test
    fun cavernSeedPoints_yWithinConfigRange() {
        val g = CavernGenerator(42L, voronoiCellSize = 256)
        val config = basicConfig(minH = 20, maxH = 60).copy(numberPerVoronoi = 5)
        for (cx in -2..2) for (cz in -2..2) {
            for (s in g.cavernSeedPoints(cx, cz, config)) {
                assertTrue(
                    s.wy in config.cavernMinHeight..config.cavernMaxHeight,
                    "seed Y=${s.wy} not in [${config.cavernMinHeight}, ${config.cavernMaxHeight}]")
            }
        }
    }

    @Test
    fun cavernSeedPoints_radiusWithinConfigRange() {
        val g = CavernGenerator(42L, voronoiCellSize = 256)
        val config =
            basicConfig().copy(cavernMinRadius = 10, cavernMaxRadius = 30, numberPerVoronoi = 8)
        for (cx in -3..3) for (cz in -3..3) {
            for (s in g.cavernSeedPoints(cx, cz, config)) {
                assertTrue(
                    s.radius in config.cavernMinRadius..config.cavernMaxRadius,
                    "seed radius=${s.radius} not in [${config.cavernMinRadius}, ${config.cavernMaxRadius}]")
            }
        }
    }

    // ── numberPerVoronoi ───────────────────────────────────────────────────────

    @Test
    fun carve_moreSeedsProduceMoreOrEqualCaveAir() {
        val config1 = basicConfig().copy(numberPerVoronoi = 1)
        val config5 = basicConfig().copy(numberPerVoronoi = 5)
        val b1 = solidChunk()
        val b5 = solidChunk()
        gen().carve(b1, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config1 })
        gen().carve(b5, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config5 })
        assertTrue(b5.count { it == airWire } >= b1.count { it == airWire })
    }

    @Test
    fun carve_zeroSeedsEquivalentToOne() {
        val config0 = basicConfig().copy(numberPerVoronoi = 0)
        val config1 = basicConfig().copy(numberPerVoronoi = 1)
        val b0 = solidChunk()
        val b1 = solidChunk()
        gen().carve(b0, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config0 })
        gen().carve(b1, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config1 })
        assertTrue(b0.contentEquals(b1))
    }

    // ── patatoid radius ────────────────────────────────────────────────────────

    @Test
    fun carve_largerRadius_moreAirThanSmaller() {
        val small = basicConfig().copy(cavernMinRadius = 5, cavernMaxRadius = 8)
        val large = basicConfig().copy(cavernMinRadius = 30, cavernMaxRadius = 50)
        val bs = solidChunk()
        val bl = solidChunk()
        gen().carve(bs, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> small })
        gen().carve(bl, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> large })
        val airSmall = bs.count { it == airWire }
        val airLarge = bl.count { it == airWire }
        assertTrue(
            airLarge >= airSmall,
            "larger radius must produce at least as much air (small=$airSmall, large=$airLarge)")
    }

    // ── cross-chunk continuity ─────────────────────────────────────────────────

    @Test
    fun carve_crossChunkContinuity_borderColumnsLargelyAgree() {
        val config = basicConfig()
        val bA = solidChunk()
        val bB = solidChunk()
        gen().carve(bA, 0, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config })
        gen().carve(bB, 16, 0, surfaceAt = { _, _ -> 80 }, cavernsAt = { _, _ -> config })
        var mismatches = 0
        for (lz in 0 until WorldConstants.CHUNK_SIZE) {
            for (y in config.cavernMinHeight..config.cavernMaxHeight) {
                val inA = bA[Chunk.index(15, y, lz)] == airWire
                val inB = bB[Chunk.index(0, y, lz)] == airWire
                if (inA != inB) mismatches++
            }
        }
        assertTrue(
            mismatches < 50,
            "border columns should mostly agree across chunks (mismatches=$mismatches)")
    }
}
