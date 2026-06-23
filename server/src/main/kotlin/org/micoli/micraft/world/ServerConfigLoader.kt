package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import org.micoli.micraft.DEBUG_WORLD
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val log = LoggerFactory.getLogger("ServerConfigLoader")

@Serializable
data class WorldSection(
    val worldMinY: Int = 0,
    val worldMaxY: Int = 1024,
    val chunkSize: Int = 16,
    val viewRadius: Int = 3,
    val forwardViewRadius: Int = 7,
)

@Serializable
data class PlayerSection(
    val heightStanding: Float = 1.8f,
    val heightSneaking: Float = 1.5f,
    val heightCrawling: Float = 0.6f,
    val width: Float = 0.6f,
    val eyeOffsetStanding: Float = 1.62f,
    val eyeOffsetSneaking: Float = 1.27f,
    val eyeOffsetCrawling: Float = 0.4f,
    val speedStanding: Float = 4.5f,
    val speedSneaking: Float = 1.3f,
    val speedCrawling: Float = 1.0f,
)

@Serializable
data class GameplaySection(
    val tickMs: Long = 50L,
    val gravity: Float = -20f,
    val jumpSpeed: Float = 8.5f,
    val flyVerticalSpeed: Float = 8f,
    val saveIntervalSeconds: Int = 30,
    val spawnX: Float = 8f,
    val spawnY: Float = 200f,
    val spawnZ: Float = 8f,
)

@Serializable
data class ServerConfig(
    val world: WorldSection = WorldSection(),
    val player: PlayerSection = PlayerSection(),
    val gameplay: GameplaySection = GameplaySection(),
)

fun loadServerConfig(path: Path): ServerConfig {
    val config = if (path.exists()) {
        runCatching { Yaml.default.decodeFromString(ServerConfig.serializer(), path.readText()) }
            .getOrElse { e ->
                log.warn("Failed to parse server.yaml ({}), using defaults", e.message)
                ServerConfig()
            }
    } else {
        log.info("No server.yaml at {}, creating with defaults", path.toAbsolutePath())
        ServerConfig()
    }
    path.parent?.createDirectories()
    path.writeText(Yaml.default.encodeToString(ServerConfig.serializer(), config))
    return config
}

fun applyServerConfig(config: ServerConfig) {
    with(config.world) {
        WorldConstants.WORLD_MIN_Y = worldMinY
        WorldConstants.WORLD_MAX_Y = worldMaxY
        WorldConstants.CHUNK_SIZE = chunkSize
        WorldConstants.VIEW_RADIUS = viewRadius
        WorldConstants.FORWARD_VIEW_RADIUS = forwardViewRadius
    }
    with(config.player) {
        PlayerConstants.HEIGHT_STANDING = heightStanding
        PlayerConstants.HEIGHT_SNEAKING = heightSneaking
        PlayerConstants.HEIGHT_CRAWLING = heightCrawling
        PlayerConstants.WIDTH = width
        PlayerConstants.EYE_OFFSET_STANDING = eyeOffsetStanding
        PlayerConstants.EYE_OFFSET_SNEAKING = eyeOffsetSneaking
        PlayerConstants.EYE_OFFSET_CRAWLING = eyeOffsetCrawling
        PlayerConstants.SPEED_STANDING = speedStanding
        PlayerConstants.SPEED_SNEAKING = speedSneaking
        PlayerConstants.SPEED_CRAWLING = speedCrawling
    }
    with(config.gameplay) {
        org.micoli.micraft.TICK_MS = tickMs
        org.micoli.micraft.GRAVITY = gravity
        org.micoli.micraft.JUMP_SPEED = jumpSpeed
        org.micoli.micraft.FLY_VERTICAL_SPEED = flyVerticalSpeed
        org.micoli.micraft.SAVE_INTERVAL_TICKS = (saveIntervalSeconds * 1000L / tickMs).toInt()
        org.micoli.micraft.SPAWN_X = spawnX
        if (!DEBUG_WORLD) {
            org.micoli.micraft.SPAWN_Y = spawnY
            org.micoli.micraft.SPAWN_Z = spawnZ
        }
    }
    log.info(
        "Server config applied: world={}, player={}, gameplay={}",
        config.world, config.player, config.gameplay,
    )
}
