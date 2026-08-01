package org.micoli.micraft.game.npc

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("NpcConfigLoader")

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
        NpcConstants.live =
            NpcConstants.live.copy(
                wanderPauseTicksMin = config.wanderPauseTicksMin,
                wanderPauseTicksMax = config.wanderPauseTicksMax,
                wanderStepTicksMax = config.wanderStepTicksMax,
                interactionRange = config.interactionRange,
                updateRange = config.updateRange,
                maxSpawnAttemptsPerTick = config.maxSpawnAttemptsPerTick,
                jumpVelocity = config.jumpVelocity,
                gameDayDurationSeconds = config.gameDayDurationSeconds,
            )
    }
}
