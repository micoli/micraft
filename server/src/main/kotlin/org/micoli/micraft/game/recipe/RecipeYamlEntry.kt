package org.micoli.micraft.game.recipe

import kotlinx.serialization.Serializable

@Serializable
data class RecipeYamlEntry(
    val giveType: String = "item",
    val giveId: String = "",
    val giveAmount: Int = 1,
    val items: List<String> = emptyList(),
)
