package org.micoli.micraft.npc

import kotlinx.serialization.Serializable

/**
 * Why an NPC died.
 *
 * Carried by the kill hook so that the caller — metrics, quests, XP — can tell a predation kill
 * from a natural one. Without it, a population chart cannot distinguish "the wolves are eating
 * everything" from "everything is dying of old age", which are opposite balance problems.
 */
@Serializable
enum class NpcDeathCause {
    /** Hit points reached zero: an attack, or a damage-over-time effect. */
    KILLED,
    /** Reached `animal.lifespanDays`. */
    OLD_AGE,
    /** Stayed at maximum hunger for `animal.starvationDeathDays`. */
    STARVATION,
}
