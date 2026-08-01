package org.micoli.micraft.game.npc

import kotlin.random.Random

/**
 * Everything a behavior needs beyond the world: the tunables in force and the random source.
 * Injecting the source lets a simulation replay a seed and lets tests assert parity between the
 * live game loop and the world simulator.
 */
data class NpcTickContext(
    val tuning: NpcTuning = NpcConstants.live,
    val random: Random = Random,
) {
    companion object {
        /** Context backed by the live server tunables. */
        val live: NpcTickContext
            get() = NpcTickContext(NpcConstants.live, Random)
    }
}
