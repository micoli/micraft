package org.micoli.micraft.game.world.vegetation

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.BlockPos

@Serializable
data class GrowingBlock(
    val pos: BlockPos,
    val chainName: String,
    val stageIndex: Int,
    val ticksAccumulated: Int,
    val ticksRequired: Int,
)
