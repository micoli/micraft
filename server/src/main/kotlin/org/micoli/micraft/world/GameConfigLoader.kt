package org.micoli.micraft.world

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

private val log = LoggerFactory.getLogger("GameConfigLoader")

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GameConfig(
    @EncodeDefault(ALWAYS) val tickMs: Long = 50L,
    @EncodeDefault(ALWAYS) val gravity: Float = -20f,
    @EncodeDefault(ALWAYS) val jumpSpeed: Float = 8.5f,
    @EncodeDefault(ALWAYS) val flyVerticalSpeed: Float = 8f,
    @EncodeDefault(ALWAYS) val saveIntervalSeconds: Int = 30,
    @EncodeDefault(ALWAYS) val spawnX: Float = 8f,
    @EncodeDefault(ALWAYS) val spawnY: Float = 200f,
    @EncodeDefault(ALWAYS) val spawnZ: Float = 8f,
    @EncodeDefault(ALWAYS) val ticksPerDay: Long = 72_000L,
    @EncodeDefault(ALWAYS) val timeBroadcastTicks: Int = 20,
    @EncodeDefault(ALWAYS) val maxInteractionDistance: Double = 7.0,
    @EncodeDefault(ALWAYS) val debugWorld: Boolean = false,
    @EncodeDefault(ALWAYS) val reconcileToleranceXz: Double = 0.5,
    @EncodeDefault(ALWAYS) val reconcileToleranceY: Double = 0.99,
)

fun loadGameConfig(path: Path): GameConfig {
    val config =
        if (path.exists()) {
            runCatching { Yaml.default.decodeFromString(GameConfig.serializer(), path.readText()) }
                .getOrElse { e ->
                    log.warn("Failed to parse game.yaml ({}), using defaults", e.message)
                    GameConfig()
                }
        } else {
            log.info("No game.yaml at {}, creating with defaults", path.toAbsolutePath())
            GameConfig()
        }
    path.parent?.createDirectories()
    path.writeText(Yaml.default.encodeToString(GameConfig.serializer(), config))
    return config
}

fun applyGameConfig(config: GameConfig) {
    with(config) {
        org.micoli.micraft.DEBUG_WORLD = debugWorld
        org.micoli.micraft.TICK_MS = tickMs
        org.micoli.micraft.GRAVITY = gravity
        org.micoli.micraft.JUMP_SPEED = jumpSpeed
        org.micoli.micraft.FLY_VERTICAL_SPEED = flyVerticalSpeed
        org.micoli.micraft.SAVE_INTERVAL_TICKS = (saveIntervalSeconds * 1000L / tickMs).toInt()
        org.micoli.micraft.TICKS_PER_DAY = ticksPerDay
        org.micoli.micraft.TIME_BROADCAST_TICKS = timeBroadcastTicks
        org.micoli.micraft.MAX_INTERACTION_DISTANCE = maxInteractionDistance
        org.micoli.micraft.RECONCILE_TOLERANCE_XZ = reconcileToleranceXz
        org.micoli.micraft.RECONCILE_TOLERANCE_Y = reconcileToleranceY
        org.micoli.micraft.SPAWN_X = spawnX
        if (debugWorld) {
            org.micoli.micraft.SPAWN_Y = 1f
            org.micoli.micraft.SPAWN_Z = 14f
        } else {
            org.micoli.micraft.SPAWN_Y = spawnY
            org.micoli.micraft.SPAWN_Z = spawnZ
        }
    }
    log.info("Game config applied: {}", config)
}
