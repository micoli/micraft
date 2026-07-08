package org.micoli.micraft.game.rpg

import kotlinx.serialization.Serializable

@Serializable
data class SourcesConfig(
    val commonPerLevel: Int = 50,
    val elitePerLevel: Int = 200,
    val bossPerLevel: Int = 1000,
)
