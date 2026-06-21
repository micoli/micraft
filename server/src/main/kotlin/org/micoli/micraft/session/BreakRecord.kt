package org.micoli.micraft.session

import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.WorldItem

data class BreakRecord(
    val pos: BlockPos,
    val blockType: BlockType,
    val spawnedItems: List<WorldItem>,
)
