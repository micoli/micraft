package org.micoli.micraft.game.item

import kotlinx.serialization.Serializable

@Serializable
data class ItemYamlEntry(
    val buildable: Boolean = false,
    val placesBlock: String? = null,
    val label: String = "",
    val bg: String = "",
    val healthRestore: Int = 0,
    val manaRestore: Int = 0,
)
