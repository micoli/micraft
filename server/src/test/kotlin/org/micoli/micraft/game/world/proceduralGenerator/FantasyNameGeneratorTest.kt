package org.micoli.micraft.game.world.proceduralGenerator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FantasyNameGeneratorTest {

    @Test
    fun generate_isDeterministicForSameInputs() {
        val a = FantasyNameGenerator.generate(42L, 3, -2)
        val b = FantasyNameGenerator.generate(42L, 3, -2)
        assertEquals(a, b)
    }

    @Test
    fun generate_differsForDifferentCells() {
        val a = FantasyNameGenerator.generate(42L, 0, 0)
        val b = FantasyNameGenerator.generate(42L, 1, 0)
        val c = FantasyNameGenerator.generate(42L, 0, 1)
        assertNotEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun generate_differsForDifferentSeeds() {
        val a = FantasyNameGenerator.generate(1L, 0, 0)
        val b = FantasyNameGenerator.generate(2L, 0, 0)
        assertNotEquals(a, b)
    }

    @Test
    fun generate_producesNonEmptyAlphabeticName() {
        for (cx in -3..3) {
            for (cz in -3..3) {
                val name = FantasyNameGenerator.generate(99L, cx, cz)
                assertTrue(name.isNotBlank(), "name blank at ($cx,$cz)")
                assertTrue(name.all { it.isLetter() }, "non-letter chars in '$name'")
            }
        }
    }

    @Test
    fun generate_lengthInReasonableRange() {
        for (cx in -5..5) {
            for (cz in -5..5) {
                val name = FantasyNameGenerator.generate(7L, cx, cz)
                assertTrue(name.length in 5..20, "name '$name' length ${name.length} out of range")
            }
        }
    }
}
