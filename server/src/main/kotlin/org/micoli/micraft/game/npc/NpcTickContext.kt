package org.micoli.micraft.game.npc

import kotlin.random.Random
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.quest.QuestManager

/**
 * Everything a behavior needs beyond the world: the tunables in force and the random source.
 * Injecting the source lets a simulation replay a seed and lets tests assert parity between the
 * live game loop and the world simulator.
 */
data class NpcTickContext(
    val tuning: NpcTuning = NpcConstants.live,
    val random: Random = Random,
    /** Set only for the one [NpcManager.handleInteract] call routed to a quest-giver behavior. */
    val questManager: QuestManager? = null,
    /**
     * Set only by [NpcManager.handleInteract] — lets an interact behavior notify the player when it
     * silently no-ops (e.g. out of range), instead of the player seeing nothing happen. Null in
     * tick contexts and in tests that don't care about that feedback.
     */
    val i18n: I18nConfig? = null,
) {
    companion object {
        /** Context backed by the live server tunables. */
        val live: NpcTickContext
            get() = NpcTickContext(NpcConstants.live, Random)
    }
}
