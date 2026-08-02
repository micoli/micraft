package org.micoli.micraft.simulation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.npc.NpcTuning
import org.micoli.micraft.game.npc.NpcYamlOverride
import org.micoli.micraft.player.rpg.BaseStats

// ── Wire DTOs ─────────────────────────────────────────────────────────────────

/** World-space rectangle the client is currently looking at. */
@Serializable
data class SimViewport(
    val minX: Float,
    val minZ: Float,
    val maxX: Float,
    val maxZ: Float,
) {
    fun contains(x: Float, z: Float): Boolean = x >= minX && x <= maxX && z >= minZ && z <= maxZ
}

/** One running arena, as shown in the "simulations en cours" list. */
@Serializable
data class SimulationInfo(
    val id: String,
    val name: String,
    val halfSize: Int,
    val viewers: Int,
    val startedAtMs: Long,
    val tick: Long,
    val gameDay: Double,
    val npcCount: Int,
    val populationCap: Int,
    val configuredTps: Int,
    val realTps: Double,
    val paused: Boolean,
)

@Serializable
data class SimArenaDto(
    val halfSize: Int,
    val groundY: Int,
    val wallHeight: Int,
)

@Serializable
data class SimNpcDto(
    val id: String,
    val name: String,
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
    val currentHp: Int,
    val maxHp: Int,
    val level: Int,
    val isDead: Boolean,
    val aggroTargetId: String? = null,
    /** Pack this NPC hunts with, and the NPC it is hunting. */
    val packId: String? = null,
    val npcTargetId: String? = null,
    val gender: String? = null,
    val hunger: Double? = null,
    val gestationRemainingDays: Double? = null,
    val ageGameDays: Double? = null,
)

@Serializable
data class SimPlayerDto(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float,
)

@Serializable
data class SimStatsDto(
    val tick: Long,
    val gameDay: Double,
    val configuredTps: Int,
    val realTps: Double,
    val npcCount: Int,
    val paused: Boolean,
    /** Grazing food standing in the arena, and cells waiting to grow back. */
    val foodBlocks: Int = 0,
    val regrowingCells: Int = 0,
    /** Population ceiling in force; spawns are refused at this count. 0 = none. */
    val populationCap: Int = 0,
)

@Serializable
data class SimNpcDetailDto(
    val npc: SimNpcDto,
    val behaviorKey: String,
    val aggroMode: String,
    val characterClass: String,
    val xp: Int,
    val currentMana: Int,
    val maxMana: Int,
    val width: Float,
    val height: Float,
    val wanderSpeed: Float,
    val wanderRadius: Float,
    val aggroRange: Float,
    val attacks: List<String>,
    val spells: List<String>,
    val baseStats: BaseStats? = null,
    val wanderPhase: String,
    val spawnX: Float,
    val spawnZ: Float,
    val parentIds: List<String> = emptyList(),
    val preyTargetId: String? = null,
    val mateTargetId: String? = null,
    val packSize: Int? = null,
    val packEngaged: Boolean? = null,
    val diet: String? = null,
    val activeEffects: List<String> = emptyList(),
)

// ── Client → server ───────────────────────────────────────────────────────────

@Serializable
sealed class SimCommand {
    @Serializable
    @SerialName("init")
    data class Init(val config: SimulationConfig, val name: String = "") : SimCommand()

    /** Ask for the running arenas; the answer is a [SimMessage.Simulations]. */
    @Serializable @SerialName("list") data object ListSimulations : SimCommand()

    /** Watch an arena someone else started. */
    @Serializable @SerialName("attach") data class Attach(val simulationId: String) : SimCommand()

    /** Stop watching without stopping the arena. */
    @Serializable @SerialName("detach") data object Detach : SimCommand()

    /**
     * Close an arena. The list lets an operator close any of them, not only the one this socket
     * watches, so the target is explicit; a null id keeps meaning "the one I am attached to".
     */
    @Serializable
    @SerialName("stop")
    data class Stop(val simulationId: String? = null) : SimCommand()

    /** Rebuild an arena from its own config. Same targeting rule as [Stop]. */
    @Serializable
    @SerialName("restart")
    data class Restart(val simulationId: String? = null) : SimCommand()

    @Serializable @SerialName("speed") data class Speed(val ticksPerSecond: Int) : SimCommand()

    @Serializable @SerialName("step") data class Step(val count: Int = 1) : SimCommand()

    @Serializable @SerialName("inspect") data class Inspect(val npcId: String) : SimCommand()

    @Serializable
    @SerialName("spawn")
    data class Spawn(
        val type: String,
        val x: Float,
        val z: Float,
        val count: Int = 1,
        val level: Int? = null,
    ) : SimCommand()

    @Serializable
    @SerialName("playerInput")
    data class PlayerInput(
        val name: String,
        val dx: Float = 0f,
        val dz: Float = 0f,
        val yaw: Float = 0f,
        val jump: Boolean = false,
    ) : SimCommand()

    @Serializable
    @SerialName("viewport")
    data class Viewport(val viewport: SimViewport?) : SimCommand()

    @Serializable @SerialName("tuning") data class Tuning(val tuning: NpcTuning) : SimCommand()

    @Serializable
    @SerialName("defs")
    data class Defs(val overrides: Map<String, NpcYamlOverride>) : SimCommand()
}

// ── Server → client ───────────────────────────────────────────────────────────

@Serializable
sealed class SimMessage {
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(
        val simulationId: String,
        val arena: SimArenaDto,
        val config: SimulationConfig,
        val npcs: List<SimNpcDto>,
        val players: List<SimPlayerDto>,
        val stats: SimStatsDto,
        val events: List<SimEvent>,
        /** Some NPCs were left out of `npcs` to keep the frame small. */
        val truncated: Boolean = false,
        /** Grazing food as flat `[x, z, isFlower]` triples. */
        val food: List<Int> = emptyList(),
        val foodVersion: Int = 0,
        /**
         * Whole retained history, so a socket attaching mid-run gets the charts already populated.
         */
        val metrics: SimMetricsDto? = null,
    ) : SimMessage()

    @Serializable
    @SerialName("frame")
    data class Frame(
        val npcs: List<SimNpcDto>,
        val players: List<SimPlayerDto>,
        val stats: SimStatsDto,
        val events: List<SimEvent>,
        val truncated: Boolean = false,
        /** Only present when the food changed since the last frame. */
        val food: List<Int>? = null,
        val foodVersion: Int = 0,
        /**
         * Buckets touched since the last push, oldest first — the last one is still open and will
         * be sent again. Absent on most frames: the charts refresh far slower than the arena moves.
         */
        val metrics: SimMetricsDto? = null,
    ) : SimMessage()

    @Serializable
    @SerialName("npcDetail")
    data class NpcDetail(val detail: SimNpcDetailDto) : SimMessage()

    @Serializable
    @SerialName("simulations")
    data class Simulations(val simulations: List<SimulationInfo>, val attachedId: String? = null) :
        SimMessage()

    @Serializable @SerialName("stopped") data object Stopped : SimMessage()

    @Serializable @SerialName("error") data class Error(val message: String) : SimMessage()
}
