package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

@Serializable
data class RecipeDefinition(
    val giveType: String,
    val giveId: String,
    val giveAmount: Int = 1,
    val ingredients: List<RecipeIngredient>,
)

fun parseIngredient(entry: String): RecipeIngredient {
    val parts = entry.split("*")
    val type = ItemType(parts[0].trim().uppercase())
    val count = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
    return RecipeIngredient(type = type, count = count)
}
