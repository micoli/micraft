package org.micoli.micraft.game.rpg

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData

class DerivedStatsCalculatorTest {

    private fun character(baseStats: BaseStats = BaseStats(), level: Int = 1) =
        CharacterData(
            id = "1",
            name = "Hero",
            characterClass = CharacterClass.WARRIOR,
            level = level,
            baseStats = baseStats,
            currentHp = 10,
            currentMana = 10)

    @Test
    fun effectiveBaseStats_withNoArmor_returnsBaseStatsUnchanged() {
        val data = character(BaseStats(str = 12))
        assertEquals(data.baseStats, DerivedStatsCalculator.effectiveBaseStats(data))
    }

    @Test
    fun effectiveBaseStats_sumsArmorBonusesOntoBaseStats() {
        val data = character(BaseStats(str = 10, dex = 10))
        val effective =
            DerivedStatsCalculator.effectiveBaseStats(
                data, listOf(StatBonus(str = 2), StatBonus(str = 1, dex = 3)))
        assertEquals(13, effective.str)
        assertEquals(13, effective.dex)
    }

    @Test
    fun compute_baseStatsTen_producesNeutralDerivedStats() {
        val data = character(BaseStats(10, 10, 10, 10, 10, 10), level = 1)
        val derived = DerivedStatsCalculator.compute(data)
        assertEquals(10, derived.maxHp) // (10-10)/2 * 1 + 10 = 10
        assertEquals(50, derived.maxMana) // 10 * 5
        assertEquals(0, derived.meleeDmg)
        assertEquals(0, derived.rangedDmg)
        assertEquals(0, derived.spellDmg)
        assertEquals(10, derived.armorClass) // 10 + 0 + 0
    }

    @Test
    fun compute_maxHp_scalesWithLevelAndConstitution() {
        val data = character(BaseStats(con = 14), level = 5)
        val derived = DerivedStatsCalculator.compute(data)
        // floor((14-10)/2) * 5 + 10 = 2*5+10 = 20
        assertEquals(20, derived.maxHp)
    }

    @Test
    fun compute_maxHp_neverBelowOne() {
        // floor((1-10)/2.0) = -5, so at level 1: -5*1+10 = 5 (still positive here)
        val data = character(BaseStats(con = 1), level = 20)
        val derived = DerivedStatsCalculator.compute(data)
        // -5*20+10 = -90, clamped to 1
        assertEquals(1, derived.maxHp)
    }

    @Test
    fun compute_dodgePct_isCappedAt75() {
        val data = character(BaseStats(dex = 100))
        val derived = DerivedStatsCalculator.compute(data)
        assertEquals(75f, derived.dodgePct)
    }

    @Test
    fun compute_magicResistPct_neverNegative() {
        val data = character(BaseStats(wis = 1))
        val derived = DerivedStatsCalculator.compute(data)
        assertEquals(0f, derived.magicResistPct)
    }

    @Test
    fun compute_armorClass_includesArmorBonuses() {
        val data = character(BaseStats(dex = 10))
        val derived = DerivedStatsCalculator.compute(data, listOf(StatBonus(acBonus = 5)))
        assertEquals(15, derived.armorClass)
    }
}
