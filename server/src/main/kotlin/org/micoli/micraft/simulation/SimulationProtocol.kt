package org.micoli.micraft.simulation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.micoli.micraft.game.npc.NpcTuning
import org.micoli.micraft.game.npc.NpcYamlOverride
import org.micoli.micraft.player.rpg.BaseStats

// ── Wire DTOs ─────────────────────────────────────────────────────────────────

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
    val diet: String? = null,
    val activeEffects: List<String> = emptyList(),
)

// ── Client → server ───────────────────────────────────────────────────────────

@Serializable
sealed class SimCommand {
    @Serializable @SerialName("init") data class Init(val config: SimulationConfig) : SimCommand()

    @Serializable @SerialName("stop") data object Stop : SimCommand()

    @Serializable @SerialName("restart") data object Restart : SimCommand()

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
        val arena: SimArenaDto,
        val config: SimulationConfig,
        val npcs: List<SimNpcDto>,
        val players: List<SimPlayerDto>,
        val stats: SimStatsDto,
        val events: List<SimEvent>,
    ) : SimMessage()

    @Serializable
    @SerialName("frame")
    data class Frame(
        val npcs: List<SimNpcDto>,
        val players: List<SimPlayerDto>,
        val stats: SimStatsDto,
        val events: List<SimEvent>,
    ) : SimMessage()

    @Serializable
    @SerialName("npcDetail")
    data class NpcDetail(val detail: SimNpcDetailDto) : SimMessage()

    @Serializable @SerialName("stopped") data object Stopped : SimMessage()

    @Serializable @SerialName("error") data class Error(val message: String) : SimMessage()
}
