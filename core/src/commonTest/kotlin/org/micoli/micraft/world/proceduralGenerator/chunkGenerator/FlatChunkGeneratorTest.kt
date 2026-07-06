package org.micoli.micraft.world.proceduralGenerator.chunkGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos

class FlatChunkGeneratorTest {

    @Test
    fun generate_producesExpectedLayerStack() {
        val chunk = FlatChunkGenerator().generate(ChunkPos(0, 0))
        assertEquals(BlockType.BEDROCK, chunk.getBlock(0, 0, 0))
        assertEquals(BlockType.STONE, chunk.getBlock(0, 1, 0))
        assertEquals(BlockType.STONE, chunk.getBlock(0, 4, 0))
        assertEquals(BlockType.DIRT, chunk.getBlock(0, 5, 0))
        assertEquals(BlockType.DIRT, chunk.getBlock(0, 6, 0))
        assertEquals(BlockType.GRASS, chunk.getBlock(0, 7, 0))
        assertEquals(BlockType.AIR, chunk.getBlock(0, 8, 0))
    }

    @Test
    fun generate_layersAreUniformAcrossXZ() {
        val chunk = FlatChunkGenerator().generate(ChunkPos(3, -2))
        for (x in 0 until 16) {
            for (z in 0 until 16) {
                assertEquals(BlockType.GRASS, chunk.getBlock(x, 7, z))
            }
        }
    }
}
