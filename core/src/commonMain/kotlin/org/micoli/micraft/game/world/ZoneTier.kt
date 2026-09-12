package org.micoli.micraft.game.world

/**
 * Danger tier derived from [WorldState.zoneLevelAt]'s continuous zone level (itself derived from
 * distance to world origin — see VoronoiBiomeZones.zoneLevelAt). Pure mapping, no new world state.
 */
enum class ZoneTier(val tier: Int, val npcLevelRange: IntRange) {
    TIER_1(1, 1..5),
    TIER_2(2, 6..10),
    TIER_3(3, 11..15),
    TIER_4(4, 16..20),
    TIER_5(5, 21..WorldConstants.RPG_LEVEL_MAX);

    companion object {
        fun fromZoneLevel(zoneLevel: Int): ZoneTier =
            entries.firstOrNull { zoneLevel in it.npcLevelRange }
                ?: if (zoneLevel < TIER_1.npcLevelRange.first) TIER_1 else TIER_5
    }
}
