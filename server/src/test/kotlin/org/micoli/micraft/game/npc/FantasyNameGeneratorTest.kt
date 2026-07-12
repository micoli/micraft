package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FantasyNameGeneratorTest {
    @Test
    fun generate_humanType_returnsTwoWordName() {
        val name = FantasyNameGenerator.generate("villager")
        val parts = name.split(" ")
        assertEquals(2, parts.size, "Human name should be 'First Last'")
    }

    @Test
    fun generate_orcType_returnsNameWithTitle() {
        val name = FantasyNameGenerator.generate("orc")
        assertTrue(name.isNotBlank())
        // Orc names are "Name the Title" or "Name Bonecrusher" etc.
        assertTrue(name.contains(" "), "Orc name should contain a space")
    }

    @Test
    fun generate_goblinType_usesOrcRace() {
        repeat(10) {
            val name = FantasyNameGenerator.generate("goblin")
            assertTrue(name.isNotBlank())
        }
    }

    @Test
    fun generate_elfType_returnsTwoWordName() {
        val name = FantasyNameGenerator.generate("elf")
        val parts = name.split(" ")
        assertEquals(2, parts.size, "Elf name should be 'First Last'")
    }

    @Test
    fun generate_dwarfType_returnsTwoWordName() {
        val name = FantasyNameGenerator.generate("dwarf")
        val parts = name.split(" ")
        assertEquals(2, parts.size, "Dwarf name should be 'First Last'")
    }

    @Test
    fun generate_firstLetterCapitalized() {
        repeat(20) {
            val name = FantasyNameGenerator.generate("wolf")
            assertTrue(name.first().isUpperCase(), "Name should start with uppercase: $name")
        }
    }

    @Test
    fun generate_animalType_fallsBackToHuman() {
        val name = FantasyNameGenerator.generate("polar_bear")
        val parts = name.split(" ")
        assertEquals(2, parts.size, "Animal NPC falls back to human naming: $name")
    }

    @Test
    fun generate_producesVariedNames() {
        val names = (1..20).map { FantasyNameGenerator.generate("goat") }.toSet()
        assertTrue(names.size > 1, "Generator should produce varied names, got: $names")
    }

    @Test
    fun generate_noEmptyParts() {
        repeat(50) {
            val name = FantasyNameGenerator.generate("wolf")
            name.split(" ").forEach { part ->
                assertTrue(part.isNotBlank(), "Name part should not be blank in: $name")
            }
        }
    }
}
