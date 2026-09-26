package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertEquals

class ChunkLayoutTest {
    @Test
    fun `strides step to the x, y and z neighbours`() {
        val i = Chunk.index(3, 40, 7)

        assertEquals(Chunk.index(4, 40, 7), i + Chunk.STRIDE_X)
        assertEquals(Chunk.index(3, 41, 7), i + Chunk.STRIDE_Y)
        assertEquals(Chunk.index(3, 40, 8), i + 1)
        assertEquals(Triple(3, 40, 7), Chunk.indexToXYZ(i))
    }

    @Test
    fun `a wire buffer decodes to the blocks it encoded`() {
        val chunk =
            Chunk.empty(ChunkPos(0, 0))
                .withBlock(5, 12, 9, BlockType.AIR, state = 2, extraState = 1)
        val withBlock =
            Chunk.of(
                ChunkPos(0, 0), chunk.blocks.toByteArray().also { it[Chunk.index(2, 30, 4)] = 1 })

        val decoded = Chunk.decodeWire(withBlock.pos, withBlock.topY(), withBlock.encodeWire())

        assertEquals(30, decoded.topY())
        assertEquals(1, decoded.blocks[Chunk.index(2, 30, 4)])
        assertEquals(2, chunk.getState(5, 12, 9))
        assertEquals(1, chunk.getExtraState(5, 12, 9))
    }
}
