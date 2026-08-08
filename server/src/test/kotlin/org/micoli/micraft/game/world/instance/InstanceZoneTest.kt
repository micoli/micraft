package org.micoli.micraft.game.world.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.ChunkPos

class InstanceZoneTest {
    private fun zone(chunks: Set<ChunkPos>, yMin: Int, yMax: Int) =
        InstanceZone(
            id = "z",
            name = "Zone",
            yMin = yMin,
            yMax = yMax,
            chunks = chunks,
            ownerName = "Alice",
            createdAt = 0,
        )

    @Test
    fun blockColumnsByLayer_isYMajor_soATruncatedStreamIsACleanSlice() {
        val z = zone(setOf(ChunkPos(0, 0), ChunkPos(1, 0)), yMin = 0, yMax = 2)
        val columns = z.blockColumnsByLayer(chunkSize = 16).toList()

        // Every coordinate at y=0 across both chunks comes before any coordinate at y=1.
        val lastYZeroIndex = columns.indexOfLast { it.second == 0 }
        val firstYOneIndex = columns.indexOfFirst { it.second == 1 }
        assertTrue(lastYZeroIndex < firstYOneIndex)

        // A cap cutting the stream mid-layer still covers both chunks for the layers it did
        // finish, rather than leaving one chunk's column entirely unstreamed.
        val perLayer = 16 * 16 * 2 // two chunks, 16x16 each
        assertEquals(perLayer, columns.count { it.second == 0 })
    }

    @Test
    fun blockColumnsByLayer_coversExactVolume() {
        val z = zone(setOf(ChunkPos(0, 0), ChunkPos(2, 3)), yMin = 5, yMax = 7)
        val columns = z.blockColumnsByLayer(chunkSize = 16).toList()
        assertEquals(16 * 16 * 2 * 3, columns.size)
        assertEquals(columns.size, columns.toSet().size)
    }

    @Test
    fun blockColumnsForChunk_returnsOnlyThatChunksFullYRange() {
        val z = zone(setOf(ChunkPos(0, 0), ChunkPos(1, 0)), yMin = 5, yMax = 7)
        val columns = z.blockColumnsForChunk(chunkSize = 16, cx = 1, cz = 0).toList()

        assertEquals(16 * 16 * 3, columns.size)
        assertTrue(columns.all { it.first in 16 until 32 })
        assertTrue(columns.all { it.second in 5..7 })
        assertTrue(columns.all { it.third in 0 until 16 })
    }

    @Test
    fun blockColumnsForChunk_outsideZone_isEmpty() {
        val z = zone(setOf(ChunkPos(0, 0)), yMin = 0, yMax = 2)
        assertEquals(0, z.blockColumnsForChunk(chunkSize = 16, cx = 5, cz = 5).count())
    }
}
