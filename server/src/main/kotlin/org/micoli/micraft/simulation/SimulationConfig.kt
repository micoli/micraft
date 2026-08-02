package org.micoli.micraft.simulation

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.npc.NpcConstants
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
    /** Per-biome NPC cap applied by the auto-spawner; 0 disables it. */
    val maxNpcs: Int = 0,
    /**
     * Hard ceiling on the arena's population, births included. Reproduction is exponential, so
     * without this an arena left running drowns both the simulation and the browser. 0 = no cap.
     */
    val populationCap: Int = 1_000,
    /**
     * Most NPCs sent in one frame. The rest are dropped from the payload — the exact count is still
     * reported in the stats, and the client keeps whatever fits its viewport.
     *
     * Kept at [populationCap] by default: a cap below it means a zoomed-out view of a full arena is
     * *always* truncated, which reads as a bug rather than as the protection it is. The frame
     * cadence is what pays for a crowded arena (see `frameIntervalFor`), not a clipped population.
     */
    val maxNpcsPerFrame: Int = 1_000,
    /**
     * Share of ground cells carrying grazing food (FLOWER/WEED). Herbivores eat nothing else, so 0
     * starves them.
     */
    val vegetationDensity: Double = 0.08,
    /** Shortens a game day so gestation and lifespan land in seconds instead of hours. */
    val gameDayDurationSeconds: Double = 60.0,
    /**
     * Game days to run before pausing. 0 = run until someone stops it.
     *
     * Pausing rather than closing: the point of a bounded run is to read what came out of it, and
     * the charts, the event log and every NPC are only inspectable while the arena is still there.
     */
    val maxGameDays: Double = 0.0,
    /**
     * Defaults to whatever the running server is using, not to the code defaults.
     *
     * The admin UI prefills this from `NpcConstants.live`, but a config posted without it used to
     * fall back to `NpcTuning()` — so an arena could silently run on different tunables than the
     * game it was meant to describe.
     */
    val npcTuning: NpcTuning = NpcConstants.live,
    val npcDefinitionOverrides: Map<String, NpcYamlOverride> = emptyMap(),
    val initialSpawns: List<SimSpawn> = emptyList(),
    val players: List<SimPlayerSpec> = emptyList(),
    /**
     * On by default so an arena populates itself like the real world does; it still needs a player
     * nearby, exactly as the live spawner requires.
     */
    val autoSpawnEnabled: Boolean = true,
)
