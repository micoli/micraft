package org.micoli.micraft.game.world

import java.nio.file.Files
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyChunkLayoutTest {
    @Test
    fun `a chunk file written in the old x-major layout loads with its blocks in place`() {
        val flat = ByteArray(Chunk.TOTAL)
        flat[LegacyChunkLayout.index(3, 7, 5)] = 9
        flat[LegacyChunkLayout.index(15, 200, 0)] = 4

        val storage = LegacyChunkLayout.toStorage(flat)

        assertEquals(9, storage[Chunk.index(3, 7, 5)])
        assertEquals(4, storage[Chunk.index(15, 200, 0)])
        assertEquals(flat.toList(), LegacyChunkLayout.fromStorage(storage).toList())
    }

    @Test
    fun `entity master indices convert both ways`() {
        val legacy = LegacyChunkLayout.index(2, 64, 11)

        val idx = LegacyChunkLayout.toStorageIndex(legacy)

        assertEquals(Chunk.index(2, 64, 11), idx)
        assertEquals(legacy, LegacyChunkLayout.fromStorageIndex(idx))
    }

    @Test
    fun `saving keeps the on-disk format x-major, entities included`() {
        val dir = Files.createTempDirectory("legacy-layout")
        val persistence = WorldPersistence(dir)
        val pos = ChunkPos(1, -2)
        val entity =
            BlockEntity(
                masterIdx = Chunk.index(4, 30, 6),
                type = BlockType.AIR,
                sizeX = 1,
                sizeY = 1,
                sizeZ = 1)
        val chunk = Chunk.empty(pos).withBlock(4, 30, 6, BlockType.AIR, state = 3).addEntity(entity)

        persistence.saveChunk(pos, chunk)

        val states =
            GZIPInputStream(dir.resolve("chunks/1_-2.mcs.gz").toFile().inputStream()).use {
                it.readBytes()
            }
        assertEquals(3, states[LegacyChunkLayout.index(4, 30, 6)])
        val loaded = persistence.loadChunk(pos)!!
        assertEquals(3, loaded.getState(4, 30, 6))
        assertEquals(Chunk.index(4, 30, 6), loaded.entityMasters.single().masterIdx)
    }
}
