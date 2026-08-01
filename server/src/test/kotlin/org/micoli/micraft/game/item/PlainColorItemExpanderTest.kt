package org.micoli.micraft.game.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemDefinition
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.PlainColor

class PlainColorItemExpanderTest {

    private val brick = BlockType("LEGO_BRICK")
    private val plate = BlockType("LEGO_PLATE")
    private val stone = BlockType("STONE")

    private val blocks =
        mapOf(
            brick to BlockDefinition(plainColorable = true),
            plate to BlockDefinition(plainColorable = true),
            stone to BlockDefinition(),
        )

    private val palette = listOf(PlainColor("blue", 0, 85, 191), PlainColor("red", 196, 40, 27))

    private val base =
        mapOf(
            ItemType("LEGO_BRICK") to
                ItemDefinition(
                    buildable = true, placesBlock = brick, label = "LGO", bg = "#DC3232"),
            ItemType("STONE") to
                ItemDefinition(
                    buildable = true, placesBlock = stone, label = "STN", bg = "#7A7A7A"),
        )

    @Test
    fun generatesOneItemPerColorableBlockAndColor() {
        val result = expandPlainColorItems(base, blocks, palette)
        assertEquals(base.size + 2 * 2, result.size)
        assertTrue(result.containsKey(ItemType("LEGO_BRICK_BLUE")))
        assertTrue(result.containsKey(ItemType("LEGO_PLATE_RED")))
    }

    @Test
    fun variant_placesBaseBlockWithItsColor() {
        val variant = expandPlainColorItems(base, blocks, palette)[ItemType("LEGO_BRICK_BLUE")]!!
        assertEquals(brick, variant.placesBlock)
        assertEquals("blue", variant.plainColor)
        assertEquals("#0055BF", variant.bg)
        assertTrue(variant.buildable)
    }

    @Test
    fun variant_reusesLabelOfTexturedItem() {
        val result = expandPlainColorItems(base, blocks, palette)
        assertEquals("LGO", result[ItemType("LEGO_BRICK_BLUE")]?.label)
        // LEGO_PLATE has no textured item in base → fallback label derived from the block id
        assertEquals("LEG", result[ItemType("LEGO_PLATE_BLUE")]?.label)
    }

    @Test
    fun nonColorableBlock_getsNoVariant() {
        val result = expandPlainColorItems(base, blocks, palette)
        assertFalse(result.containsKey(ItemType("STONE_BLUE")))
        assertNull(result[ItemType("STONE")]?.plainColor)
    }

    @Test
    fun explicitYamlItem_isNotOverwritten() {
        val handWritten =
            ItemDefinition(buildable = false, placesBlock = stone, label = "XXX", bg = "#000000")
        val result =
            expandPlainColorItems(
                base + (ItemType("LEGO_BRICK_BLUE") to handWritten), blocks, palette)
        assertEquals(handWritten, result[ItemType("LEGO_BRICK_BLUE")])
    }

    @Test
    fun addingAColor_addsOneItemPerColorableBlock() {
        val before = expandPlainColorItems(base, blocks, palette).size
        val after =
            expandPlainColorItems(base, blocks, palette + PlainColor("gold", 255, 215, 0)).size
        assertEquals(before + 2, after)
    }

    @Test
    fun emptyPaletteOrNoColorableBlock_returnsBaseUnchanged() {
        assertEquals(base, expandPlainColorItems(base, blocks, emptyList()))
        assertEquals(base, expandPlainColorItems(base, mapOf(stone to BlockDefinition()), palette))
    }

    @Test
    fun isIdempotent() {
        val once = expandPlainColorItems(base, blocks, palette)
        assertEquals(once, expandPlainColorItems(once, blocks, palette))
    }
}
