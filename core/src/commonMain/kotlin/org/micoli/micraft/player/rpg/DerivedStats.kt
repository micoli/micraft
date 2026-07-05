package org.micoli.micraft.player.rpg

import kotlinx.serialization.Serializable

@Serializable
data class DerivedStats(
    val maxHp: Int,
    val maxMana: Int,
    val meleeDmg: Int,
    val rangedDmg: Int,
    val spellDmg: Int,
    val critChancePct: Float,
    val critDmgMult: Float,
    val dodgePct: Float,
    val magicResistPct: Float,
    val initiative: Int,
    val hpRegenPerSec: Float,
    val manaRegenPerSec: Float,
    val armorClass: Int,
)
