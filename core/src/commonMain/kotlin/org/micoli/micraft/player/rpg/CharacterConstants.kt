package org.micoli.micraft.player.rpg

object CharacterConstants {
    val POINT_BUY_COST = mapOf(8 to 0, 9 to 1, 10 to 2, 11 to 3, 12 to 4, 13 to 5, 14 to 7, 15 to 9)
    const val POINT_BUY_BUDGET = 27
    const val STAT_MIN_BUY = 8
    const val STAT_MAX_BUY = 15
    const val STAT_MAX_TOTAL = 20

    // Cumulative XP required to reach each level (index = level - 1, based on D&D 5e)
    val XP_THRESHOLDS =
        listOf(
            0,
            300,
            900,
            2700,
            6500,
            14000,
            23000,
            34000,
            48000,
            64000,
            85000,
            100000,
            120000,
            140000,
            165000,
            195000,
            225000,
            265000,
            305000,
            355000,
        )

    fun xpToNextLevel(level: Int, currentXp: Int): Int {
        if (level >= 20) return 0
        return (XP_THRESHOLDS[level] - currentXp).coerceAtLeast(0)
    }

    fun levelFor(xp: Int): Int = (XP_THRESHOLDS.indexOfLast { xp >= it } + 1).coerceIn(1, 20)
}
