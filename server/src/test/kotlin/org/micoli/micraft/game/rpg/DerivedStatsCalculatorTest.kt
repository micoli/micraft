package org.micoli.micraft.game.rpg

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.player.rpg.BaseStats

class DerivedStatsCalculatorTest {

    private fun compute(baseStats: BaseStats = BaseStats(), level: Int = 1, acBonus: Int = 0) =
        DerivedStatsCalculator.compute(baseStats, level, acBonus)

    @Test
    fun effectiveBaseStats_withNoArmor_returnsBaseStatsUnchanged() {
        val base = BaseStats(str = 12)
        assertEquals(base, DerivedStatsCalculator.effectiveBaseStats(base, emptyList()))
    }

    @Test
    fun effectiveBaseStats_sumsArmorBonusesOntoBaseStats() {
        val effective =
            DerivedStatsCalculator.effectiveBaseStats(
                BaseStats(str = 10, dex = 10),
                listOf(StatBonus(str = 2), StatBonus(str = 1, dex = 3)))
        assertEquals(13, effective.str)
        assertEquals(13, effective.dex)
    }

    @Test
    fun compute_baseStatsTen_producesNeutralDerivedStats() {
        val derived = compute(BaseStats(10, 10, 10, 10, 10, 10), level = 1)
        assertEquals(10, derived.maxHp) // (10-10)/2 * 1 + 10 = 10
        assertEquals(50, derived.maxMana) // 10 * 5
        assertEquals(0, derived.meleeDmg)
        assertEquals(0, derived.rangedDmg)
        assertEquals(0, derived.spellDmg)
        assertEquals(10, derived.armorClass) // 10 + 0 + 0
        assertEquals(1, derived.maxTokens) // level 1: 1/4+1 = 1
    }

    @Test
    fun compute_maxHp_scalesWithLevelAndConstitution() {
        val derived = compute(BaseStats(con = 14), level = 5)
        // floor((14-10)/2) * 5 + 10 = 2*5+10 = 20
        assertEquals(20, derived.maxHp)
    }

    @Test
    fun compute_maxHp_neverBelowOne() {
        // floor((1-10)/2.0) = -5, so at level 1: -5*1+10 = 5 (still positive here)
        val derived = compute(BaseStats(con = 1), level = 20)
        // -5*20+10 = -90, clamped to 1
        assertEquals(1, derived.maxHp)
    }

    @Test
    fun compute_dodgePct_isCappedAt60() {
        val derived = compute(BaseStats(dex = 100))
        assertEquals(60f, derived.dodgePct)
    }

    @Test
    fun compute_dodgePct_neverNegative() {
        val derived = compute(BaseStats(dex = 1))
        assertEquals(0f, derived.dodgePct)
    }

    @Test
    fun compute_dodgePct_followsAdr0013Formula() {
        // (DEX - 10) * 1.5
        val derived = compute(BaseStats(dex = 14))
        assertEquals(6f, derived.dodgePct)
    }

    @Test
    fun compute_magicResistPct_isCappedAt60() {
        val derived = compute(BaseStats(wis = 100))
        assertEquals(60f, derived.magicResistPct)
    }

    @Test
    fun compute_magicResistPct_neverNegative() {
        val derived = compute(BaseStats(wis = 1))
        assertEquals(0f, derived.magicResistPct)
    }

    @Test
    fun compute_maxTokens_scalesWithLevel() {
        assertEquals(1, compute(level = 1).maxTokens)
        assertEquals(2, compute(level = 4).maxTokens)
        assertEquals(3, compute(level = 8).maxTokens)
        assertEquals(6, compute(level = 20).maxTokens)
    }

    @Test
    fun compute_armorClass_includesArmorBonuses() {
        val derived = compute(BaseStats(dex = 10), acBonus = 5)
        assertEquals(15, derived.armorClass)
    }
}
