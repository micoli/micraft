package org.micoli.micraft.world

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.PlainColor
import org.micoli.micraft.game.world.PlainColorRegistry

class PlainColorRegistryTest {

    private val blue = PlainColor("blue", 0, 85, 191)
    private val red = PlainColor("red", 196, 40, 27)

    @AfterTest
    fun tearDown() {
        PlainColorRegistry.load(emptyList())
    }

    @Test
    fun indexZero_isUntintedSentinel() {
        PlainColorRegistry.load(listOf(blue, red))
        assertNull(PlainColorRegistry.byIndex(0))
        assertEquals(blue, PlainColorRegistry.byIndex(1))
        assertEquals(red, PlainColorRegistry.byIndex(2))
    }

    @Test
    fun indexOf_isInverseOfByIndex() {
        PlainColorRegistry.load(listOf(blue, red))
        assertEquals(1, PlainColorRegistry.indexOf("blue"))
        assertEquals(2, PlainColorRegistry.indexOf("RED"))
    }

    @Test
    fun indexOf_unknownOrBlank_isZero() {
        PlainColorRegistry.load(listOf(blue))
        assertEquals(0, PlainColorRegistry.indexOf("chartreuse"))
        assertEquals(0, PlainColorRegistry.indexOf(null))
        assertEquals(0, PlainColorRegistry.indexOf(""))
    }

    @Test
    fun load_replacesPreviousPalette() {
        PlainColorRegistry.load(listOf(blue, red))
        PlainColorRegistry.load(listOf(red))
        assertEquals(1, PlainColorRegistry.size())
        assertEquals(listOf(red), PlainColorRegistry.all())
        assertEquals(0, PlainColorRegistry.indexOf("blue"))
    }

    @Test
    fun load_truncatesBeyondSixBits() {
        PlainColorRegistry.load((1..80).map { PlainColor("c$it", it, it, it) })
        assertEquals(BlockState.MAX_COLOR_INDEX, PlainColorRegistry.size())
        assertNull(PlainColorRegistry.byIndex(BlockState.MAX_COLOR_INDEX + 1))
    }

    @Test
    fun hex_isUppercaseSixDigits() {
        PlainColorRegistry.load(listOf(blue))
        assertEquals("0055BF", PlainColorRegistry.hex(1))
        assertNull(PlainColorRegistry.hex(0))
    }

    @Test
    fun fromHex_parsesWithAndWithoutHash() {
        assertEquals(blue, PlainColor.fromHex("blue", "0055BF"))
        assertEquals(blue, PlainColor.fromHex("blue", "#0055bf"))
        assertNull(PlainColor.fromHex("bad", "12345"))
        assertNull(PlainColor.fromHex("bad", "GGGGGG"))
    }
}
