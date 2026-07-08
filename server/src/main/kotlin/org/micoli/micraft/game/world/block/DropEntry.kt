package org.micoli.micraft.game.world.block

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.ItemType

@Serializable
data class DropEntry(
    val item: ItemType,
    val dropRate: Int = 100,
    val minCount: Int = 1,
    val maxCount: Int = 1,
)
