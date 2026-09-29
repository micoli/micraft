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
    /** [SpellType.PROTECTION] only — how long the granted Status effect lasts. */
    val durationSec: Float = 0f,
    /** [SpellType.PROTECTION] only — Armor class bonus while active. */
    val acBonus: Int = 0,
    /** [SpellType.PROTECTION] only — Dodge % bonus while active. */
    val dodgeBonusPct: Float = 0f,
    /** [SpellType.PROTECTION] only — Magic resistance % bonus while active. */
    val magicResistBonusPct: Float = 0f,
    /** [SpellType.PROTECTION] only — max HP bonus while active. */
    val maxHpBonus: Int = 0,
    /** [SpellType.PROTECTION] only — HP regen multiplier bonus while active (0.5 = +50%). */
    val hpRegenMultBonus: Float = 0f,
)
