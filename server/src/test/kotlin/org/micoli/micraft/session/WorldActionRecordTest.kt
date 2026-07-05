package org.micoli.micraft.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ItemType

class WorldActionRecordTest {
    @Test
    fun break_record_storesFields() {
        val pos = BlockPos(1, 2, 3)
        val record =
            WorldActionRecord.Break(
                pos = pos, blockType = BlockType.STONE, spawnedItems = emptyList())
        assertEquals(pos, record.pos)
        assertEquals(BlockType.STONE, record.blockType)
        assertTrue(record.spawnedItems.isEmpty())
    }

    @Test
    fun place_record_storesFields() {
        val pos = BlockPos(4, 5, 6)
        val item = ItemType("COBBLESTONE")
        val record = WorldActionRecord.Place(pos = pos, itemType = item)
        assertEquals(pos, record.pos)
        assertEquals(item, record.itemType)
    }

    @Test
    fun break_isNotPlace() {
        val record: WorldActionRecord =
            WorldActionRecord.Break(BlockPos(0, 0, 0), BlockType.AIR, emptyList())
        assertTrue(record is WorldActionRecord.Break)
    }

    @Test
    fun place_isNotBreak() {
        val record: WorldActionRecord = WorldActionRecord.Place(BlockPos(0, 0, 0), ItemType("DIRT"))
        assertTrue(record is WorldActionRecord.Place)
    }
}
