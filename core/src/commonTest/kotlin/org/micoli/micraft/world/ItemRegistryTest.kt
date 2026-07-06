package org.micoli.micraft.world

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemRegistryTest {

    @AfterTest
    fun clear() {
        ItemRegistry.load(emptyMap())
    }

    @Test
    fun get_unknownItem_returnsDefaultDefinition() {
        assertEquals(ItemDefinition(), ItemRegistry.get(ItemType("UNKNOWN")))
    }

    @Test
    fun load_thenGet_returnsLoadedDefinition() {
        val type = ItemType("COBBLESTONE")
        ItemRegistry.load(
            mapOf(type to ItemDefinition(buildable = true, placesBlock = BlockType.STONE)))
        val def = ItemRegistry.get(type)
        assertTrue(def.buildable)
        assertEquals(BlockType.STONE, def.placesBlock)
    }

    @Test
    fun load_replacesPreviousContent() {
        val a = ItemType("A")
        val b = ItemType("B")
        ItemRegistry.load(mapOf(a to ItemDefinition(buildable = true)))
        ItemRegistry.load(mapOf(b to ItemDefinition(buildable = true)))
        assertEquals(setOf(b), ItemRegistry.keys())
        assertEquals(ItemDefinition(), ItemRegistry.get(a))
    }

    @Test
    fun keys_reflectsLoadedItems() {
        ItemRegistry.load(
            mapOf(ItemType("X") to ItemDefinition(), ItemType("Y") to ItemDefinition()))
        assertEquals(setOf(ItemType("X"), ItemType("Y")), ItemRegistry.keys())
    }

    @Test
    fun buildableExtension_delegatesToRegistry() {
        val type = ItemType("FLINT")
        ItemRegistry.load(mapOf(type to ItemDefinition(buildable = true)))
        assertTrue(type.buildable)
    }

    @Test
    fun placesBlockExtension_delegatesToRegistry() {
        val type = ItemType("SAND")
        ItemRegistry.load(mapOf(type to ItemDefinition(placesBlock = BlockType.SAND)))
        assertEquals(BlockType.SAND, type.placesBlock)
    }
}
