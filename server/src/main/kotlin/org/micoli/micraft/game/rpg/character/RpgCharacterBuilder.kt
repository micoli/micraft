package org.micoli.micraft.game.rpg.character

import java.util.UUID
import org.micoli.micraft.game.rpg.CharacterConstants
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterClass
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.DerivedStats

sealed interface RpgCharacterResult {
    data class Success(val character: CharacterData, val derived: DerivedStats) :
        RpgCharacterResult

    data class Failure(val kind: Kind, val cost: Int = 0) : RpgCharacterResult

    enum class Kind {
        NAME_LENGTH,
        STAT_RANGE,
        BUDGET_EXCEEDED,
    }
}

/**
 * Single source of truth for turning a point-buy allocation + class into a fresh level-1
 * [CharacterData]. Used by `/api/character/rpgcreate`, the `/createcharacter` slash command and
 * `POST /api/admin/players`. Class resolution and any i18n/HTTP error mapping stay with the caller.
 */
object RpgCharacterBuilder {
    fun build(
        name: String,
        characterClass: CharacterClass,
        str: Int,
        dex: Int,
        intel: Int,
        wis: Int,
        con: Int,
        cha: Int,
        characterId: String = UUID.randomUUID().toString(),
    ): RpgCharacterResult {
        if (name.length !in 3..24)
            return RpgCharacterResult.Failure(RpgCharacterResult.Kind.NAME_LENGTH)

        val allocated = listOf(str, dex, intel, wis, con, cha)
        if (allocated.any {
            it !in CharacterConstants.STAT_MIN_BUY..CharacterConstants.STAT_MAX_BUY
        }) {
            return RpgCharacterResult.Failure(RpgCharacterResult.Kind.STAT_RANGE)
        }
        val cost = allocated.sumOf { CharacterConstants.POINT_BUY_COST[it] ?: 9 }
        if (cost > CharacterConstants.POINT_BUY_BUDGET) {
            return RpgCharacterResult.Failure(RpgCharacterResult.Kind.BUDGET_EXCEEDED, cost)
        }

        fun withBonus(value: Int, bonus: Int) =
            (value + bonus).coerceIn(1, CharacterConstants.STAT_MAX_TOTAL)
        val finalStats =
            BaseStats(
                str = withBonus(str, characterClass.strBonus),
                dex = withBonus(dex, characterClass.dexBonus),
                intel = withBonus(intel, characterClass.intelBonus),
                wis = withBonus(wis, characterClass.wisBonus),
                con = withBonus(con, characterClass.conBonus),
                cha = withBonus(cha, characterClass.chaBonus),
            )
        val prelim =
            CharacterData(
                id = characterId,
                name = name,
                characterClass = characterClass,
                baseStats = finalStats,
                currentHp = 0,
                currentMana = 0,
            )
        val derived = DerivedStatsCalculator.compute(prelim)
        return RpgCharacterResult.Success(
            prelim.copy(currentHp = derived.maxHp, currentMana = derived.maxMana), derived)
    }
}
