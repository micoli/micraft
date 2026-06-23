package org.micoli.micraft.world

import kotlinx.serialization.Serializable

@Serializable
data class BlockDefinition(
    val hardness: Int = 1,
    val solid: Boolean = true,
    val transparent: Boolean = false,
    val minimapColor: List<Int> = listOf(128, 128, 128),
    val modelElement: String = "",
)
