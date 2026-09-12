package org.micoli.micraft.game.world

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoneTierTest {
    @Test
    fun mapsZoneLevelToTheRightTier() {
        assertEquals(ZoneTier.TIER_1, ZoneTier.fromZoneLevel(1))
        assertEquals(ZoneTier.TIER_1, ZoneTier.fromZoneLevel(5))
        assertEquals(ZoneTier.TIER_2, ZoneTier.fromZoneLevel(6))
        assertEquals(ZoneTier.TIER_2, ZoneTier.fromZoneLevel(10))
        assertEquals(ZoneTier.TIER_3, ZoneTier.fromZoneLevel(11))
        assertEquals(ZoneTier.TIER_4, ZoneTier.fromZoneLevel(20))
        assertEquals(ZoneTier.TIER_5, ZoneTier.fromZoneLevel(21))
        assertEquals(ZoneTier.TIER_5, ZoneTier.fromZoneLevel(WorldConstants.RPG_LEVEL_MAX))
    }

    @Test
    fun clampsOutOfRangeLevels() {
        assertEquals(ZoneTier.TIER_1, ZoneTier.fromZoneLevel(0))
        assertEquals(ZoneTier.TIER_5, ZoneTier.fromZoneLevel(999))
    }
}
