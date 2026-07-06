package org.micoli.micraft.world.proceduralGenerator.chunkGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos

class DebugChunkGeneratorTest {

    @Test
    fun generate_originChunk_hasSingleGrassBlock() {
        val chunk = DebugChunkGenerator().generate(ChunkPos(0, 0))
        assertEquals(BlockType.GRASS, chunk.getBlock(8, 2, 8))
        assertEquals(BlockType.AIR, chunk.getBlock(8, 3, 8))
        assertEquals(BlockType.AIR, chunk.getBlock(0, 0, 0))
    }

    @Test
    fun generate_otherChunks_areEntirelyAir() {
        val chunk = DebugChunkGenerator().generate(ChunkPos(1, 0))
        assertEquals(BlockType.AIR, chunk.getBlock(8, 2, 8))
    }
}
