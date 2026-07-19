package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

@Serializable
data class NpcSpawnConfigRaw(
    val autoSpawn: Boolean = false,
    val maxPerChunk: Int = 1,
    val spawnBiomes: List<String> = emptyList(),
)
