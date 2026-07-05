package org.micoli.micraft.player.rpg

import kotlinx.serialization.Serializable

@Serializable
data class BaseStats(
    val str: Int = 8,
    val dex: Int = 8,
    val intel: Int = 8,
    val wis: Int = 8,
    val con: Int = 8,
    val cha: Int = 8,
)
