package org.micoli.micraft.world

import kotlinx.serialization.Serializable

enum class BlockType { AIR, BEDROCK, STONE, DIRT, GRASS }

@Serializable
data class BlockPos(val x: Int, val y: Int, val z: Int) {
    init {
        require(y in WorldConstants.WORLD_MIN_Y..WorldConstants.WORLD_MAX_Y) {
            "y=$y out of bounds [${WorldConstants.WORLD_MIN_Y}, ${WorldConstants.WORLD_MAX_Y}]"
        }
    }
}
