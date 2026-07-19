package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

@Serializable
data class NpcSpawnConfigRawOverride(
    val autoSpawn: Boolean? = null,
    val maxPerChunk: Int? = null,
    val spawnBiomes: List<String>? = null,
)
