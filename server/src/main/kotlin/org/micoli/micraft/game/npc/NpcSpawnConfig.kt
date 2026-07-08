package org.micoli.micraft.game.npc

data class NpcSpawnConfig(
    val autoSpawn: Boolean = false,
    val maxTotal: Int = 0,
    val maxPerChunk: Int = 1,
    val spawnBiomes: List<String> = emptyList(),
)
