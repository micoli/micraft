package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable

@Serializable
data class SpellRankDefinition(
    val rageGain: Int = 20,
    val tokenCost: Int = 0,
    val manaCost: Int = 0,
    val rageCost: Int = 0,
    val cooldownMs: Long = 0L,
    val aoeRadius: Float = 0f,
    val maxRange: Float = 15f,
    /** [SpellType.DIRECT_DAMAGE] only — flat damage dealt, no dice roll. */
    val power: Int = 0,
    /**
     * [SpellType.NECROTIC_AOE] only — name of a [org.micoli.micraft.combat.StatusEffect] data
     * object (e.g. "Frozen", "Stunned"), resolved by [resolveStatusEffect]; defaults to
     * [org.micoli.micraft.combat.StatusEffect.Withering] when unset or unrecognized.
     */
    val statusEffect: String? = null,
)
