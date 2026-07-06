package org.micoli.micraft.world.proceduralGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.WorldConstants

class ProceduralChunkGeneratorTest {

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
}
