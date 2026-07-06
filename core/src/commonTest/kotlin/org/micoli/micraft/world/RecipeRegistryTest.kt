package org.micoli.micraft.world

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeRegistryTest {

    @AfterTest
    fun clear() {
        RecipeRegistry.load(emptyMap())
    }

    private fun recipe() =
        RecipeDefinition(
            giveType = "block",
            giveId = "STONE",
            giveAmount = 1,
            ingredients = listOf(RecipeIngredient(ItemType("COBBLESTONE"), 4)))

    @Test
    fun get_unknownRecipe_returnsNull() {
        assertNull(RecipeRegistry.get("nope"))
    }

    @Test
    fun load_thenGet_returnsLoadedRecipe() {
        RecipeRegistry.load(mapOf("stone_recipe" to recipe()))
        assertEquals(recipe(), RecipeRegistry.get("stone_recipe"))
    }

    @Test
    fun load_replacesPreviousContent() {
        RecipeRegistry.load(mapOf("a" to recipe()))
        RecipeRegistry.load(mapOf("b" to recipe()))
        assertEquals(setOf("b"), RecipeRegistry.keys())
        assertNull(RecipeRegistry.get("a"))
    }

    @Test
    fun all_returnsSnapshotCopy() {
        RecipeRegistry.load(mapOf("a" to recipe()))
        val snapshot = RecipeRegistry.all()
        RecipeRegistry.load(mapOf("b" to recipe()))
        assertEquals(setOf("a"), snapshot.keys)
    }

    @Test
    fun parseIngredient_parsesTypeAndCount() {
        val ing = parseIngredient("cobblestone*4")
        assertEquals(ItemType("COBBLESTONE"), ing.type)
        assertEquals(4, ing.count)
    }

    @Test
    fun parseIngredient_defaultsCountToOne() {
        val ing = parseIngredient("flint")
        assertEquals(ItemType("FLINT"), ing.type)
        assertEquals(1, ing.count)
    }
}
