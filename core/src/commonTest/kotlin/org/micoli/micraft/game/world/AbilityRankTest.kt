package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AbilityRankTest {
    @Test
    fun mapsLevelToTheRankOfItsDangerTierBand() {
        assertEquals(1, AbilityRank.forLevel(1))
        assertEquals(1, AbilityRank.forLevel(5))
        assertEquals(2, AbilityRank.forLevel(6))
        assertEquals(2, AbilityRank.forLevel(10))
        assertEquals(3, AbilityRank.forLevel(11))
        assertEquals(3, AbilityRank.forLevel(15))
        assertEquals(4, AbilityRank.forLevel(16))
        assertEquals(4, AbilityRank.forLevel(20))
        assertEquals(5, AbilityRank.forLevel(21))
        assertEquals(5, AbilityRank.forLevel(WorldConstants.RPG_LEVEL_MAX))
    }

    @Test
    fun levelZeroUsesRankOne() {
        assertEquals(1, AbilityRank.forLevel(0))
    }

    @Test
    fun picksTheComputedRankWhenTheAbilityDefinesIt() {
        assertEquals(2, AbilityRank.usable(setOf(1, 2, 3), level = 7))
    }

    @Test
    fun fallsBackToTheHighestLowerRankWhenTheComputedOneIsMissing() {
        assertEquals(2, AbilityRank.usable(setOf(1, 2, 5), level = 16))
    }

    @Test
    fun skipsTheAbilityWhenItOnlyDefinesHigherRanks() {
        assertNull(AbilityRank.usable(setOf(3, 4), level = 7))
    }
}
