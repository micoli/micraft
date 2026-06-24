package org.micoli.micraft.world

import kotlinx.serialization.Serializable

@Serializable
enum class ItemType {
    COBBLESTONE,
    DIRT,
    SAND,
    GRAVEL,
    SANDSTONE,
    SNOWBALL,
    FLINT,
}

@Serializable
enum class BlockType {
    AIR,
    BEDROCK,
    STONE,
    DIRT,
    GRASS,
    SAND,
    SANDSTONE,
    GRAVEL,
    SNOW,
    OAK_LOG,
    OAK_LEAVES,
    PINE_LOG,
    PINE_LEAVES,
    PINE_LEAVES_SNOW,
    FLOWER,
    WEED,
}

val BlockType.hardness: Int
    get() = BlockRegistry.get(this).hardness.let { if (it == -1) Int.MAX_VALUE else it }

val BlockType.isSolid: Boolean
    get() = BlockRegistry.get(this).solid

val ItemType.buildable: Boolean
    get() = ItemRegistry.get(this).buildable

val ItemType.placesBlock: BlockType?
    get() = ItemRegistry.get(this).placesBlock

@Serializable
data class BlockPos(val x: Int, val y: Int, val z: Int) {
    init {
        require(y in WorldConstants.WORLD_MIN_Y..WorldConstants.WORLD_MAX_Y) {
            "y=$y out of bounds [${WorldConstants.WORLD_MIN_Y}, ${WorldConstants.WORLD_MAX_Y}]"
        }
    }
}
