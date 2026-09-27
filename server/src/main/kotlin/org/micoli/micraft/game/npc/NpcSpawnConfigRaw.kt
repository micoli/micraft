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
    /** Share of its Region's budget relative to the other NPC types of the Roster. */
    val weight: Int = 1,
)
