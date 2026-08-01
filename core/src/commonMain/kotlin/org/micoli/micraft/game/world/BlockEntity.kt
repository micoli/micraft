package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

@Serializable
data class BlockEntity(
    val masterIdx: Int,
    val type: BlockType,
    val sizeX: Int,
    val sizeY: Int = 1,
    val sizeZ: Int = 1,
    val rotation: Int = 0,
    val yOffset: Int = 0,
    val colorIndex: Int = 0,
)
