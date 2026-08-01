package org.micoli.micraft.simulation

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.npc.NpcTuning
import org.micoli.micraft.game.npc.NpcYamlOverride

/** An NPC batch to place when the arena starts. */
@Serializable
data class SimSpawn(
    val type: String,
    val count: Int = 1,
    val level: Int? = null,
)

/** A scriptable stand-in player. */
@Serializable
data class SimPlayerSpec(
    val name: String,
    val x: Float = 0f,
    val z: Float = 0f,
)

/**
 * Everything needed to build a disposable in-memory world. Tunables and per-type overrides are
 * instance-scoped: nothing here reaches the live server world.
 */
@Serializable
data class SimulationConfig(
    val halfSize: Int = 100,
    val groundY: Int = 7,
    val wallHeight: Int = 4,
    /** Simulation ticks per real second. 0 pauses; the tick delta itself never changes. */
    val ticksPerSecond: Int = 200,
    val seed: Long = 42L,
    /** Zone level the arena reports, driving NPC level windows on spawn. */
    val zoneLevel: Int = 5,
    /** Per-biome NPC cap; 0 disables the cap. */
    val maxNpcs: Int = 0,
    /** Shortens a game day so gestation and lifespan land in seconds instead of hours. */
    val gameDayDurationSeconds: Double = 60.0,
    val npcTuning: NpcTuning = NpcTuning(),
    val npcDefinitionOverrides: Map<String, NpcYamlOverride> = emptyMap(),
    val initialSpawns: List<SimSpawn> = emptyList(),
    val players: List<SimPlayerSpec> = emptyList(),
    val autoSpawnEnabled: Boolean = false,
)
