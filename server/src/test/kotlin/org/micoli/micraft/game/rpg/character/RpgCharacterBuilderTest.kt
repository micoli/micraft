package org.micoli.micraft.game.rpg.character

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.micoli.micraft.player.rpg.CharacterClass

class RpgCharacterBuilderTest {

    private fun build(
        name: String = "Alice",
        cc: CharacterClass = CharacterClass.WARRIOR,
        str: Int = 8,
        dex: Int = 8,
        intel: Int = 8,
        wis: Int = 8,
        con: Int = 8,
        cha: Int = 8,
    ) = RpgCharacterBuilder.build(name, cc, str, dex, intel, wis, con, cha)

    @Test
    fun warrior_allEights_appliesClassBonusAndDerivesHpMana() {
        val r = assertIs<RpgCharacterResult.Success>(build())
        assertEquals(10, r.character.baseStats.str, "WARRIOR str +2")
        assertEquals(9, r.character.baseStats.con, "WARRIOR con +1")
        assertEquals(8, r.character.baseStats.dex)
        assertEquals(1, r.character.level)
        assertEquals(r.derived.maxHp, r.character.currentHp)
        assertEquals(r.derived.maxMana, r.character.currentMana)
    }

    @Test
    fun shortName_isNameLengthFailure() {
        assertEquals(
            RpgCharacterResult.Kind.NAME_LENGTH,
            assertIs<RpgCharacterResult.Failure>(build(name = "Al")).kind)
    }

    @Test
    fun statOutOfBuyRange_isStatRangeFailure() {
        assertEquals(
            RpgCharacterResult.Kind.STAT_RANGE,
            assertIs<RpgCharacterResult.Failure>(build(str = 16)).kind)
    }

    @Test
    fun overBudget_isBudgetExceededWithCost() {
        // six stats at 15 => 6 * 9 = 54, well over the 27 budget.
        val f =
            assertIs<RpgCharacterResult.Failure>(
                build(str = 15, dex = 15, intel = 15, wis = 15, con = 15, cha = 15))
        assertEquals(RpgCharacterResult.Kind.BUDGET_EXCEEDED, f.kind)
        assertEquals(54, f.cost)
    }
}
