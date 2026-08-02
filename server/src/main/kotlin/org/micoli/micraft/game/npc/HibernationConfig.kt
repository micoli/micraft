package org.micoli.micraft.game.npc

import kotlin.math.absoluteValue
import kotlinx.serialization.Serializable

/** In-game hours in one game day — the unit `hoursPerCycle` is expressed in. */
const val HOURS_PER_GAME_DAY = 24.0

/**
 * Periodic hibernation: the NPC sleeps [hoursPerCycle] in-game hours out of every [cycleDays] game
 * days. A sleeping NPC neither moves nor acquires a target; with [wakeOnDamage] it is pulled out of
 * its sleep for the rest of the current window as soon as it takes a hit.
 */
@Serializable
data class HibernationConfig(
    val hoursPerCycle: Double = 0.0,
    val cycleDays: Double = 0.0,
    val wakeOnDamage: Boolean = true,
) {
    val enabled: Boolean
        get() = hoursPerCycle > 0.0 && cycleDays > 0.0

    /** Sleep window length, capped at the cycle so a misconfigured entity never sleeps forever. */
    val durationDays: Double
        get() = (hoursPerCycle / HOURS_PER_GAME_DAY).coerceAtMost(cycleDays)

    /**
     * Whether [gameDay] falls inside a sleep window. Each instance gets its own [offsetDays] so a
     * whole population does not fall asleep on the same tick.
     */
    fun isInWindow(gameDay: Double, offsetDays: Double): Boolean {
        if (!enabled) return false
        val phase = (gameDay + offsetDays).mod(cycleDays)
        return phase < durationDays
    }

    /** Stable per-instance phase shift derived from the NPC id — no RNG draw, survives a reload. */
    fun offsetFor(npcId: String): Double {
        if (!enabled) return 0.0
        return (npcId.hashCode().toLong().absoluteValue % 10_000).toDouble() / 10_000.0 * cycleDays
    }
}
