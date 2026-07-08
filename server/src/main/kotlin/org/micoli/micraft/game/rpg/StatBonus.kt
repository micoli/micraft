package org.micoli.micraft.game.rpg

import kotlinx.serialization.Serializable

@Serializable
data class StatBonus(
    val str: Int = 0,
    val dex: Int = 0,
    val intel: Int = 0,
    val wis: Int = 0,
    val con: Int = 0,
    val cha: Int = 0,
    val acBonus: Int = 0,
)
