package org.micoli.micraft.game.world.vegetation

import kotlinx.serialization.Serializable

@Serializable
data class VegetationConfigData(
    val enabled: Boolean = true,
    val growthCheckIntervalTicks: Int = 40,
    val chains: List<GrowthChain> = emptyList(),
)
