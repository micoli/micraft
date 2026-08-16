package org.micoli.micraft

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos

class ChunkTest {

    @Test
    fun empty_isAllAir() {
        val chunk = Chunk.empty(ChunkPos(0, 0))
        assertEquals(BlockType.AIR, chunk.getBlock(0, 0, 0))
        assertEquals(BlockType.AIR, chunk.getBlock(5, 10, 3))
    }

    @Test
    fun build_fillsBlocksUsingFiller() {
        val chunk =
            Chunk.build(ChunkPos(0, 0)) { x, y, z ->
                if (y == 0) BlockType.BEDROCK else BlockType.AIR
            }
        assertEquals(BlockType.BEDROCK, chunk.getBlock(3, 0, 7))
        assertEquals(BlockType.AIR, chunk.getBlock(3, 1, 7))
    }

    @Test
    fun withBlock_returnsNewChunkWithUpdatedBlock_leavesOriginalUnchanged() {
        val original = Chunk.empty(ChunkPos(0, 0))
        val updated = original.withBlock(1, 2, 3, BlockType.STONE)
        assertEquals(BlockType.AIR, original.getBlock(1, 2, 3))
        assertEquals(BlockType.STONE, updated.getBlock(1, 2, 3))
    }

    @Test
    fun topY_returnsHighestNonAirBlock() {
        val chunk =
            Chunk.build(ChunkPos(0, 0)) { _, y, _ ->
                if (y <= 5) BlockType.STONE else BlockType.AIR
            }
        assertEquals(5, chunk.topY())
    }

    @Test
    fun topY_ofEmptyChunk_isZero() {
        assertEquals(0, Chunk.empty(ChunkPos(0, 0)).topY())
    }

    @Test
    fun encodeWire_thenDecodeWire_roundTrips() {
        val original =
            Chunk.build(ChunkPos(1, -1)) { x, y, z ->
                if (y <= 2) BlockType.STONE
                else if (y == 3 && x == 0 && z == 0) BlockType.GRASS else BlockType.AIR
            }
        val wire = original.encodeWire()
        val decoded = Chunk.decodeWire(original.pos, original.topY(), wire)
        assertEquals(original, decoded)
    }

    @Test
    fun index_isUniquePerCoordinate() {
        val seen = HashSet<Int>()
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                for (z in 0 until 4) {
                    assertEquals(true, seen.add(Chunk.index(x, y, z)))
                }
            }
        }
    }

    @Test
    fun equals_comparesContentNotArrayIdentity() {
        val a = Chunk.empty(ChunkPos(0, 0))
        val b = Chunk.empty(ChunkPos(0, 0))
        assertEquals(a, b)
    }

    @Test
    fun withBlock_returnsNewChunkWithUpdatedExtraState_leavesOriginalUnchanged() {
        val original = Chunk.empty(ChunkPos(0, 0))
        val updated = original.withBlock(1, 2, 3, BlockType.STONE, extraState = 5)
        assertEquals(0, original.getExtraState(1, 2, 3).toInt())
        assertEquals(5, updated.getExtraState(1, 2, 3).toInt())
    }

    @Test
    fun encodeWireExtraStates_thenDecodeWire_roundTrips() {
        var original =
            Chunk.build(ChunkPos(1, -1)) { _, y, _ ->
                if (y == 0) BlockType.STONE else BlockType.AIR
            }
        original = original.withBlock(0, 0, 0, BlockType.STONE, extraState = 7)
        val wire = original.encodeWire()
        val wireStates = original.encodeWireStates()
        val wireExtraStates = original.encodeWireExtraStates()
        val decoded =
            Chunk.decodeWire(original.pos, original.topY(), wire, wireStates, wireExtraStates)
        assertEquals(7, decoded.getExtraState(0, 0, 0).toInt())
        assertEquals(original, decoded)
    }
}
