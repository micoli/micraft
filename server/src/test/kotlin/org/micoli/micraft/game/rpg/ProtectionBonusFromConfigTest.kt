package org.micoli.micraft.game.rpg

import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.game.combat.SkillsConfig
import org.micoli.micraft.player.rpg.BaseStats

/**
 * Exercises the real shipped Spell config end to end through [DerivedStatsCalculator]: every
 * Protection's per-Rank bonus, at neutral Base stats, matches the spec's Solution table (Rank 1 ->
 * Rank 5).
 */
class ProtectionBonusFromConfigTest {

    private val spells = SkillsConfig().data.spells

    private fun bonusAt(spellId: String, rank: Int): ProtectionBonus {
        val rankDef = spells.getValue(spellId).ranks.getValue(rank)
        return ProtectionBonus(
            acBonus = rankDef.acBonus,
            dodgeBonusPct = rankDef.dodgeBonusPct,
            magicResistBonusPct = rankDef.magicResistBonusPct,
            maxHpBonus = rankDef.maxHpBonus,
            hpRegenMultBonus = rankDef.hpRegenMultBonus,
        )
    }

    private fun armorClassAt(spellId: String, rank: Int) =
        DerivedStatsCalculator.compute(
                BaseStats(), level = 1, protectionBonus = bonusAt(spellId, rank))
            .armorClass

    private fun dodgePctAt(spellId: String, rank: Int) =
        DerivedStatsCalculator.compute(
                BaseStats(), level = 1, protectionBonus = bonusAt(spellId, rank))
            .dodgePct

    private fun magicResistPctAt(spellId: String, rank: Int) =
        DerivedStatsCalculator.compute(
                BaseStats(), level = 1, protectionBonus = bonusAt(spellId, rank))
            .magicResistPct

    private fun maxHpAt(spellId: String, rank: Int) =
        DerivedStatsCalculator.compute(
                BaseStats(), level = 1, protectionBonus = bonusAt(spellId, rank))
            .maxHp

    private fun hpRegenPerSecAt(spellId: String, rank: Int) =
        DerivedStatsCalculator.compute(
                BaseStats(), level = 1, protectionBonus = bonusAt(spellId, rank))
            .hpRegenPerSec

    @Test
    fun `Iron Skin raises Armor class by +4 +5 +6 +7 +8 for Ranks 1-5`() {
        val baseline = DerivedStatsCalculator.compute(BaseStats(), level = 1).armorClass
        assertEquals(baseline + 4, armorClassAt("iron_skin", 1))
        assertEquals(baseline + 5, armorClassAt("iron_skin", 2))
        assertEquals(baseline + 6, armorClassAt("iron_skin", 3))
        assertEquals(baseline + 7, armorClassAt("iron_skin", 4))
        assertEquals(baseline + 8, armorClassAt("iron_skin", 5))
    }

    @Test
    fun `Shadowstep raises Dodge by +30 +33 +36 +40 +45 percent for Ranks 1-5`() {
        assertEquals(30f, dodgePctAt("shadowstep", 1))
        assertEquals(33f, dodgePctAt("shadowstep", 2))
        assertEquals(36f, dodgePctAt("shadowstep", 3))
        assertEquals(40f, dodgePctAt("shadowstep", 4))
        assertEquals(45f, dodgePctAt("shadowstep", 5))
    }

    @Test
    fun `Arcane Ward raises Magic resistance and a little Armor class for Ranks 1-5`() {
        val baseline = DerivedStatsCalculator.compute(BaseStats(), level = 1).armorClass
        assertEquals(30f, magicResistPctAt("arcane_ward", 1))
        assertEquals(45f, magicResistPctAt("arcane_ward", 5))
        assertEquals(baseline + 2, armorClassAt("arcane_ward", 1))
        assertEquals(baseline + 2, armorClassAt("arcane_ward", 2))
        assertEquals(baseline + 3, armorClassAt("arcane_ward", 3))
        assertEquals(baseline + 3, armorClassAt("arcane_ward", 4))
        assertEquals(baseline + 4, armorClassAt("arcane_ward", 5))
    }

    @Test
    fun `Nature's Veil raises both Magic resistance and Dodge for Ranks 1-5`() {
        assertEquals(20f, magicResistPctAt("natures_veil", 1))
        assertEquals(30f, magicResistPctAt("natures_veil", 5))
        assertEquals(15f, dodgePctAt("natures_veil", 1))
        assertEquals(24f, dodgePctAt("natures_veil", 5))
    }

    @Test
    fun `Fortitude raises max HP by +10 +20 +35 +50 +70 and HP regen by x1_5 for Ranks 1-5`() {
        val baseline = DerivedStatsCalculator.compute(BaseStats(), level = 1)
        assertEquals(baseline.maxHp + 10, maxHpAt("fortitude", 1))
        assertEquals(baseline.maxHp + 20, maxHpAt("fortitude", 2))
        assertEquals(baseline.maxHp + 35, maxHpAt("fortitude", 3))
        assertEquals(baseline.maxHp + 50, maxHpAt("fortitude", 4))
        assertEquals(baseline.maxHp + 70, maxHpAt("fortitude", 5))
        assertEquals(baseline.hpRegenPerSec * 1.5f, hpRegenPerSecAt("fortitude", 1))
        assertEquals(baseline.hpRegenPerSec * 1.5f, hpRegenPerSecAt("fortitude", 5))
    }
}
