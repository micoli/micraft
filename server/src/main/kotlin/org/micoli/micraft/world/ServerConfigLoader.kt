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

private val log = LoggerFactory.getLogger("ServerConfigLoader")

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WorldSection(
    @EncodeDefault(ALWAYS) val worldMinY: Int = 0,
    @EncodeDefault(ALWAYS) val worldMaxY: Int = 128,
    @EncodeDefault(ALWAYS) val chunkSize: Int = 32,
    @EncodeDefault(ALWAYS) val viewRadius: Int = 3,
    @EncodeDefault(ALWAYS) val forwardViewRadius: Int = 7,
    @EncodeDefault(ALWAYS) val waterLevel: Int = 65,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PlayerSection(
    @EncodeDefault(ALWAYS) val heightStanding: Float = 1.8f,
    @EncodeDefault(ALWAYS) val heightSneaking: Float = 1.5f,
    @EncodeDefault(ALWAYS) val heightCrawling: Float = 0.6f,
    @EncodeDefault(ALWAYS) val width: Float = 0.6f,
    @EncodeDefault(ALWAYS) val eyeOffsetStanding: Float = 1.62f,
    @EncodeDefault(ALWAYS) val eyeOffsetSneaking: Float = 1.27f,
    @EncodeDefault(ALWAYS) val eyeOffsetCrawling: Float = 0.4f,
    @EncodeDefault(ALWAYS) val speedStanding: Float = 4.5f,
    @EncodeDefault(ALWAYS) val speedSneaking: Float = 1.3f,
    @EncodeDefault(ALWAYS) val speedCrawling: Float = 1.0f,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LocalAuthConfig(
    @EncodeDefault(ALWAYS) val usersFile: String = "data/config/auth/users.yaml",
    @EncodeDefault(ALWAYS) val groupsFile: String = "data/config/auth/groups.yaml",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OAuthConfig(
    @EncodeDefault(ALWAYS) val type: String = "google",
    @EncodeDefault(ALWAYS) val clientId: String = "",
    @EncodeDefault(ALWAYS) val clientSecret: String = "",
    @EncodeDefault(ALWAYS) val redirectUri: String = "http://localhost:8080/auth/callback",
    @EncodeDefault(ALWAYS) val scopes: List<String> = listOf("openid", "email", "profile"),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AuthSection(
    @EncodeDefault(ALWAYS) val provider: String = "none",
    @EncodeDefault(ALWAYS) val local: LocalAuthConfig = LocalAuthConfig(),
    val oauth: OAuthConfig? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ServerConfig(
    @EncodeDefault(ALWAYS) val world: WorldSection = WorldSection(),
    @EncodeDefault(ALWAYS) val player: PlayerSection = PlayerSection(),
    @EncodeDefault(ALWAYS) val auth: AuthSection = AuthSection(),
)

fun loadServerConfig(path: Path): ServerConfig {
    val config =
        if (path.exists()) {
            runCatching {
                    Yaml.default.decodeFromString(ServerConfig.serializer(), path.readText())
                }
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
        WorldConstants.WATER_LEVEL = waterLevel
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
    log.info(
        "Server config applied: world={}, player={}",
        config.world,
        config.player,
    )
}
