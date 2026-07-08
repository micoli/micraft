package org.micoli.micraft.game.world.vegetation

import kotlinx.serialization.Serializable

@Serializable
data class GrowthChain(
    val name: String,
    val stages: List<GrowthStage>,
    val finalTree: String,
    val requiresVegetationHost: Boolean = true,
)
