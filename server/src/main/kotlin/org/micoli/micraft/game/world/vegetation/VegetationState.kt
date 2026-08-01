package org.micoli.micraft.game.world.vegetation

import kotlinx.serialization.Serializable

@Serializable
data class VegetationState(
    val blocks: List<GrowingBlock>,
    val regrowing: List<PendingRegrowth> = emptyList(),
)
