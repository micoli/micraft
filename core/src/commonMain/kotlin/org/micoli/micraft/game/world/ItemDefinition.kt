package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

@Serializable
data class ItemDefinition(
    val buildable: Boolean = false,
    val placesBlock: BlockType? = null,
    val label: String = "",
    val bg: String = "",
    /** Palette color name this item places the block in; null = untinted (textured) variant. */
    val plainColor: String? = null,
    val healthRestore: Int = 0,
    val manaRestore: Int = 0,
    val consumable: Boolean = false,
    /**
     * Entity this item spawns instead of placing a block — mutually exclusive with [placesBlock] in
     * practice. First use: vehicle items, validated server-side against a target rail block.
     */
    val spawnsEntity: EntityType? = null,
)
