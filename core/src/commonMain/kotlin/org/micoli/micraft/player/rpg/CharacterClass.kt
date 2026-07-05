package org.micoli.micraft.player.rpg

import kotlinx.serialization.Serializable

@Serializable
enum class CharacterClass(
    val strBonus: Int = 0,
    val dexBonus: Int = 0,
    val intelBonus: Int = 0,
    val wisBonus: Int = 0,
    val conBonus: Int = 0,
    val chaBonus: Int = 0,
) {
    WARRIOR(strBonus = 2, conBonus = 1),
    MAGE(intelBonus = 2, wisBonus = 1),
    RANGER(dexBonus = 2, wisBonus = 1),
    ROGUE(dexBonus = 2, intelBonus = 1),
    CLERIC(wisBonus = 2, conBonus = 1),
}
