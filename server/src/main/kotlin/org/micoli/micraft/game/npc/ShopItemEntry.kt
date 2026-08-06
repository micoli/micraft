package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

@Serializable
data class ShopItemEntry(
    val itemType: String,
    val buyPrice: Int,
    val sellPrice: Int = 0,
)
