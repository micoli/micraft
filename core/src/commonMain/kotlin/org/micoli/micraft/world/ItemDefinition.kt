package org.micoli.micraft.world

import kotlinx.serialization.Serializable

@Serializable
data class ItemDefinition(
    val buildable: Boolean = false,
    val placesBlock: BlockType? = null,
)
