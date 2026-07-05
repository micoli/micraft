package org.micoli.micraft.rpg.character

import kotlin.math.floor
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.DerivedStats
import org.micoli.micraft.player.rpg.StatBonus

object DerivedStatsCalculator {
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

    fun compute(data: CharacterData, armorBonuses: List<StatBonus> = emptyList()): DerivedStats {
        val s = effectiveBaseStats(data, armorBonuses)
        val lvl = data.level
        return DerivedStats(
            maxHp = (floor((s.con - 10) / 2.0) * lvl + 10).toInt().coerceAtLeast(1),
            maxMana = s.wis * 5,
            meleeDmg = floor((s.str - 10) / 2.0).toInt(),
            rangedDmg = floor((s.dex - 10) / 2.0).toInt(),
            spellDmg = floor((s.intel - 10) / 2.0).toInt(),
            critChancePct = 5f + s.dex * 0.2f,
            critDmgMult = 2f,
            dodgePct = (s.dex * 2.5f).coerceAtMost(75f),
            magicResistPct = ((s.wis - 10) * 2f).coerceAtLeast(0f),
            initiative = floor((s.dex - 10) / 2.0).toInt(),
            hpRegenPerSec = s.con / 10f,
            manaRegenPerSec = s.wis / 20f,
        )
    }
}
