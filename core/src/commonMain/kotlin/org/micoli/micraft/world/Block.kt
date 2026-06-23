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

@Serializable
enum class BlockType {
    AIR, BEDROCK, STONE, DIRT, GRASS, SAND, SANDSTONE, GRAVEL, SNOW,
    OAK_LOG, OAK_LEAVES, PINE_LOG, PINE_LEAVES, PINE_LEAVES_SNOW, FLOWER, WEED,
}

val BlockType.hardness: Int get() = when (this) {
    BlockType.AIR              -> 0
    BlockType.BEDROCK          -> Int.MAX_VALUE
    BlockType.STONE            -> 5
    BlockType.DIRT             -> 3
    BlockType.GRASS            -> 3
    BlockType.SAND             -> 2
    BlockType.SANDSTONE        -> 4
    BlockType.GRAVEL           -> 3
    BlockType.SNOW             -> 1
    BlockType.OAK_LOG          -> 3
    BlockType.PINE_LOG         -> 3
    BlockType.OAK_LEAVES       -> 1
    BlockType.PINE_LEAVES      -> 1
    BlockType.PINE_LEAVES_SNOW -> 1
    BlockType.FLOWER           -> 1
    BlockType.WEED             -> 1
}

val BlockType.isSolid: Boolean get() = when (this) {
    BlockType.AIR, BlockType.FLOWER, BlockType.WEED -> false
    else -> true
}

@Serializable
data class BlockPos(val x: Int, val y: Int, val z: Int) {
    init {
        require(y in WorldConstants.WORLD_MIN_Y..WorldConstants.WORLD_MAX_Y) {
            "y=$y out of bounds [${WorldConstants.WORLD_MIN_Y}, ${WorldConstants.WORLD_MAX_Y}]"
        }
    }
}
