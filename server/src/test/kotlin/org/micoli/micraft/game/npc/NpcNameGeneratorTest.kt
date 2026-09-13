package org.micoli.micraft.game.npc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NpcNameGeneratorTest {

    @Test
    fun generate_returnsNameNotInTakenSet() {
        val taken = setOf("Astra Blackwood", "Bram Stonehall")
        val name = NpcNameGenerator.generate("human") { it in taken }
        assertFalse(name in taken)
    }

    @Test
    fun generate_fallsBackToNumberedSuffixWhenEverythingIsTaken() {
        val name = NpcNameGenerator.generate("human") { true }
        assertTrue(Regex(""".+ \d+$""").matches(name))
    }

    @Test
    fun generate_neverInvokesIsTakenWithBlank() {
        var sawBlank = false
        NpcNameGenerator.generate("human") { candidate ->
            if (candidate.isBlank()) sawBlank = true
            false
        }
        assertFalse(sawBlank)
    }
}
