package org.micoli.micraft.game.npc.resident

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ResidentTest {
    private val key = ResidentKey(regionKey = 42L, npcType = "seller", ordinal = 1)

    @Test
    fun `same World seed and Resident key give the same name and Temperament`() {
        assertEquals(Resident.of(1234L, key), Resident.of(1234L, key))
    }

    @Test
    fun `each ordinal of a type in a Region is a different Resident`() {
        val names = (1..10).map { Resident.of(1234L, key.copy(ordinal = it)).name }.toSet()

        assertEquals(10, names.size)
    }

    @Test
    fun `another World seed gives other names`() {
        val names = { seed: Long -> (1..10).map { Resident.of(seed, key.copy(ordinal = it)).name } }

        assertNotEquals(names(1234L), names(5678L))
    }

    @Test
    fun `every Temperament is drawn across Residents`() {
        val drawn = (1..60).map { Resident.of(1234L, key.copy(ordinal = it)).temperament }.toSet()

        assertEquals(Temperament.entries.toSet(), drawn)
    }

    @Test
    fun `a taken name is skipped, the same way on every build`() {
        val usual = Resident.of(1234L, key).name

        val settled = { Resident.of(1234L, key) { it.equals(usual, ignoreCase = true) } }

        assertNotEquals(usual, settled().name)
        assertEquals(settled(), settled())
    }
}
