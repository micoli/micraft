package org.micoli.micraft.npc

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("NpcConfigLoader")

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NpcConfig(
    @EncodeDefault(ALWAYS) val wanderPauseTicksMin: Int = 40,
    @EncodeDefault(ALWAYS) val wanderPauseTicksMax: Int = 120,
    @EncodeDefault(ALWAYS) val wanderStepTicksMax: Int = 60,
    @EncodeDefault(ALWAYS) val interactionRange: Float = 4f,
    @EncodeDefault(ALWAYS) val updateRange: Float = 96f,
    @EncodeDefault(ALWAYS) val spawnCheckIntervalTicks: Int = 200,
    @EncodeDefault(ALWAYS) val maxSpawnAttemptsPerTick: Int = 3,
    @EncodeDefault(ALWAYS) val jumpVelocity: Float = 6.5f,
)

class NpcConfigLoader(private val path: Path) {
    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(
                "# yaml-language-server: \$schema=../schemas/npc.schema.json\n" +
                    Yaml.default.encodeToString(NpcConfig.serializer(), NpcConfig()))
            log.info("Generated default NPC config at {}", path.toAbsolutePath())
        }
    }

    fun load(): NpcConfig {
        val config =
            runCatching { Yaml.default.decodeFromString(NpcConfig.serializer(), path.readText()) }
                .getOrElse { e ->
                    log.warn("Failed to load npc.yaml ({}), using defaults", e.message)
                    NpcConfig()
                }
        applyConfig(config)
        log.info("NPC config loaded: {}", config)
        return config
    }

    fun reload(): NpcConfig = load()

    private fun applyConfig(config: NpcConfig) {
        NpcConstants.WANDER_PAUSE_TICKS_MIN = config.wanderPauseTicksMin
        NpcConstants.WANDER_PAUSE_TICKS_MAX = config.wanderPauseTicksMax
        NpcConstants.WANDER_STEP_TICKS_MAX = config.wanderStepTicksMax
        NpcConstants.INTERACTION_RANGE = config.interactionRange
        NpcConstants.UPDATE_RANGE = config.updateRange
        NpcConstants.SPAWN_CHECK_INTERVAL_TICKS = config.spawnCheckIntervalTicks
        NpcConstants.MAX_SPAWN_ATTEMPTS_PER_TICK = config.maxSpawnAttemptsPerTick
        NpcConstants.JUMP_VELOCITY = config.jumpVelocity
    }
}
