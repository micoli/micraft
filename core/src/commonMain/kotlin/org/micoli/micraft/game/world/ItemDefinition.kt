package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

@Serializable
data class ItemDefinition(
    val buildable: Boolean = false,
    val placesBlock: BlockType? = null,
    val label: String = "",
    val bg: String = "",
)
