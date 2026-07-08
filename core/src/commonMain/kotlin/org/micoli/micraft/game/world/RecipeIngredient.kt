package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

@Serializable data class RecipeIngredient(val type: ItemType, val count: Int)
