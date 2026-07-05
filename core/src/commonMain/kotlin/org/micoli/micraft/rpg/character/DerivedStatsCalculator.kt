package org.micoli.micraft.rpg.character

import kotlin.math.floor
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.DerivedStats

object DerivedStatsCalculator {
    fun compute(data: CharacterData): DerivedStats {
        val s = data.baseStats
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
