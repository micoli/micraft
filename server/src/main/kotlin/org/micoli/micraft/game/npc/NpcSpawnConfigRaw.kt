package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

@Serializable
data class NpcSpawnConfigRaw(
    val autoSpawn: Boolean = false,
    val maxPerChunk: Int = 1,
    val spawnBiomes: List<String> = emptyList(),
    /**
     * Ceiling on how many of this type may exist in the world at once. 0 = no ceiling.
     *
     * The world had no per-type quota at all, only a per-chunk cap and a per-zone total, so the
     * spawner — not the ecology — decided the population: 1894 spawns against 502 births over 60
     * simulated days.
     */
    val maxTotal: Int = 0,
    /**
     * Floor the spawner restocks up to, and *only* up to. 0 keeps the old behaviour of spawning
     * whenever there is chunk room.
     *
     * With a floor set, the spawner becomes an anti-extinction net rather than the population's
     * engine: everything above the floor has to be born.
     */
    val minTotal: Int = 0,
)
