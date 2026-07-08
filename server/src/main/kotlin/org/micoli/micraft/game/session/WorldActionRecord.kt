package org.micoli.micraft.game.session

import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.WorldItem

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
