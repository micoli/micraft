package org.micoli.micraft.game.npc

data class NpcSpawnConfig(
    val autoSpawn: Boolean = false,
    val maxPerChunk: Int = 1,
    val spawnBiomes: List<String> = emptyList(),
)
