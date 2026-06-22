package org.micoli.micraft.session

import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ItemType
import org.micoli.micraft.world.WorldItem

sealed class WorldActionRecord {
    data class Break(
        val pos: BlockPos,
        val blockType: BlockType,
        val spawnedItems: List<WorldItem>,
    ) : WorldActionRecord()

    data class Place(
        val pos: BlockPos,
        val itemType: ItemType,
    ) : WorldActionRecord()
}
