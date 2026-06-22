package org.micoli.micraft.world

import kotlinx.serialization.Serializable

@Serializable
enum class ItemType(val buildable: Boolean, val placesBlock: BlockType?) {
    COBBLESTONE(true,  BlockType.STONE),
    DIRT       (true,  BlockType.DIRT),
    SAND       (true,  BlockType.SAND),
    GRAVEL     (true,  BlockType.GRAVEL),
    SANDSTONE  (true,  BlockType.SANDSTONE),
    SNOWBALL   (false, null),
    FLINT      (false, null),
}
