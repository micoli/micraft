package org.micoli.micraft.game.rpg

import kotlin.math.floor
import org.micoli.micraft.combat.CombatConstants
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.DerivedStats

object DerivedStatsCalculator {
    val STAT_EFFECTS: Set<StatusEffect> =
        setOf(
            StatusEffect.HpBoost,
            StatusEffect.ManaBoost,
            StatusEffect.HpRegenBoost,
            StatusEffect.ManaRegenBoost)

    fun compute(
        baseStats: BaseStats,
        level: Int,
        acBonus: Int = 0,
        effects: Collection<StatusEffect> = emptyList(),
    ): DerivedStats {
        val s = baseStats
        return DerivedStats(
            maxHp =
                (floor((s.con - 10) / 2.0) * level + 10).toInt().coerceAtLeast(1) +
                    if (StatusEffect.HpBoost in effects) 20 else 0,
            maxMana = s.wis * 5 + if (StatusEffect.ManaBoost in effects) 20 else 0,
            meleeDmg = floor((s.str - 10) / 2.0).toInt(),
            rangedDmg = floor((s.dex - 10) / 2.0).toInt(),
            spellDmg = floor((s.intel - 10) / 2.0).toInt(),
            critChancePct = 5f + s.dex * 0.2f,
            critDmgMult = 2f,
            dodgePct = ((s.dex - 10) * 1.5f).coerceIn(0f, CombatConstants.DODGE_CAP_PCT),
            magicResistPct = ((s.wis - 10) * 2f).coerceIn(0f, CombatConstants.MAGIC_RESIST_CAP_PCT),
            initiative = floor((s.dex - 10) / 2.0).toInt(),
            hpRegenPerSec = s.con / 10f * if (StatusEffect.HpRegenBoost in effects) 1.1f else 1f,
            manaRegenPerSec =
                s.wis / 20f * if (StatusEffect.ManaRegenBoost in effects) 1.1f else 1f,
            armorClass = 10 + acBonus + floor((s.dex - 10) / 2.0).toInt(),
            maxTokens = level / 4 + 1,
        )
    }

    fun effectiveBaseStats(baseStats: BaseStats, bonuses: List<StatBonus>): BaseStats =
        if (bonuses.isEmpty()) baseStats
        else
            BaseStats(
                str = baseStats.str + bonuses.sumOf { it.str },
                dex = baseStats.dex + bonuses.sumOf { it.dex },
                intel = baseStats.intel + bonuses.sumOf { it.intel },
                wis = baseStats.wis + bonuses.sumOf { it.wis },
                con = baseStats.con + bonuses.sumOf { it.con },
                cha = baseStats.cha + bonuses.sumOf { it.cha },
            )
}
