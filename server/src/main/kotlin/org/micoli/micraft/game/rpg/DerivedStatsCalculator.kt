package org.micoli.micraft.game.rpg

import kotlin.math.floor
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.DerivedStats

object DerivedStatsCalculator {
    fun compute(
        baseStats: BaseStats,
        level: Int,
        activeEffects: Set<String> = emptySet(),
    ): DerivedStats {
        val s = baseStats
        return DerivedStats(
            maxHp =
                (floor((s.con - 10) / 2.0) * level + 10).toInt().coerceAtLeast(1) +
                    if ("HpBoost" in activeEffects) 20 else 0,
            maxMana = s.wis * 5 + if ("ManaBoost" in activeEffects) 20 else 0,
            meleeDmg = floor((s.str - 10) / 2.0).toInt(),
            rangedDmg = floor((s.dex - 10) / 2.0).toInt(),
            spellDmg = floor((s.intel - 10) / 2.0).toInt(),
            critChancePct = 5f + s.dex * 0.2f,
            critDmgMult = 2f,
            dodgePct = (s.dex * 2.5f).coerceAtMost(75f),
            magicResistPct = ((s.wis - 10) * 2f).coerceAtLeast(0f),
            initiative = floor((s.dex - 10) / 2.0).toInt(),
            hpRegenPerSec = s.con / 10f * if ("HpRegenBoost" in activeEffects) 1.1f else 1f,
            manaRegenPerSec = s.wis / 20f * if ("ManaRegenBoost" in activeEffects) 1.1f else 1f,
            armorClass = 10 + floor((s.dex - 10) / 2.0).toInt(),
            maxTokens = level / 4 + 1,
        )
    }

    fun effectiveBaseStats(
        data: CharacterData,
        armorBonuses: List<StatBonus> = emptyList()
    ): BaseStats =
        if (armorBonuses.isEmpty()) data.baseStats
        else
            BaseStats(
                str = data.baseStats.str + armorBonuses.sumOf { it.str },
                dex = data.baseStats.dex + armorBonuses.sumOf { it.dex },
                intel = data.baseStats.intel + armorBonuses.sumOf { it.intel },
                wis = data.baseStats.wis + armorBonuses.sumOf { it.wis },
                con = data.baseStats.con + armorBonuses.sumOf { it.con },
                cha = data.baseStats.cha + armorBonuses.sumOf { it.cha },
            )

    fun compute(
        data: CharacterData,
        armorBonuses: List<StatBonus> = emptyList(),
        activeEffects: Set<String> = emptySet(),
    ): DerivedStats {
        val s = effectiveBaseStats(data, armorBonuses)
        val lvl = data.level
        return DerivedStats(
            maxHp =
                (floor((s.con - 10) / 2.0) * lvl + 10).toInt().coerceAtLeast(1) +
                    if ("HpBoost" in activeEffects) 20 else 0,
            maxMana = s.wis * 5 + if ("ManaBoost" in activeEffects) 20 else 0,
            meleeDmg = floor((s.str - 10) / 2.0).toInt(),
            rangedDmg = floor((s.dex - 10) / 2.0).toInt(),
            spellDmg = floor((s.intel - 10) / 2.0).toInt(),
            critChancePct = 5f + s.dex * 0.2f,
            critDmgMult = 2f,
            dodgePct = (s.dex * 2.5f).coerceAtMost(75f),
            magicResistPct = ((s.wis - 10) * 2f).coerceAtLeast(0f),
            initiative = floor((s.dex - 10) / 2.0).toInt(),
            hpRegenPerSec = s.con / 10f * if ("HpRegenBoost" in activeEffects) 1.1f else 1f,
            manaRegenPerSec = s.wis / 20f * if ("ManaRegenBoost" in activeEffects) 1.1f else 1f,
            armorClass = 10 + armorBonuses.sumOf { it.acBonus } + floor((s.dex - 10) / 2.0).toInt(),
            maxTokens = lvl / 4 + 1,
        )
    }
}
