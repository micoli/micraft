package org.micoli.micraft.game.world

/** The Rank an NPC uses: the one of its Level's band, the bands being the [ZoneTier] ones. */
object AbilityRank {
    fun forLevel(level: Int): Int = ZoneTier.fromZoneLevel(level).tier

    /**
     * The highest of [definedRanks] an NPC of [level] may use, or null when the Ability only
     * defines higher Ranks.
     */
    fun usable(definedRanks: Set<Int>, level: Int): Int? {
        val cap = forLevel(level)
        return definedRanks.filter { it <= cap }.maxOrNull()
    }
}
