package org.micoli.micraft.game.rpg

/**
 * The defense bonuses a Character's active Protection Spell (Iron Skin, Shadowstep, ...)
 * contributes to [DerivedStatsCalculator.compute], resolved from the live Spell config for the Rank
 * that granted it (spec: Protection Spells).
 */
data class ProtectionBonus(
    val acBonus: Int = 0,
    val dodgeBonusPct: Float = 0f,
    val magicResistBonusPct: Float = 0f,
    val maxHpBonus: Int = 0,
    val hpRegenMultBonus: Float = 0f,
)
