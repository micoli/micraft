package org.micoli.micraft.game.world.vegetation

import kotlinx.serialization.Serializable

@Serializable
data class GrowthStage(
    val block: String,
    val minTicks: Int,
    val maxTicks: Int,
)
