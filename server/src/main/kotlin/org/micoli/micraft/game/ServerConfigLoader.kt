package org.micoli.micraft.game

import java.nio.file.Path
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableConfig
import org.micoli.micraft.game.world.PlayerConstants
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.MessageEncoding
import org.micoli.micraft.schema.JsonSchemaRoot
import org.slf4j.LoggerFactory

private val serverConfigLog = LoggerFactory.getLogger("ServerConfigLoader")

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WorldSection(
    @EncodeDefault(ALWAYS) val worldMinY: Int = 0,
    @EncodeDefault(ALWAYS) val worldMaxY: Int = 256,
    @EncodeDefault(ALWAYS) val chunkSize: Int = 16,
    @EncodeDefault(ALWAYS) val viewRadius: Int = 3,
    @EncodeDefault(ALWAYS) val forwardViewRadius: Int = 7,
    @EncodeDefault(ALWAYS) val waterLevel: Int = 65,
    /** See WorldConstants.IMPOSTOR_SKIRT_DEPTH. */
    @EncodeDefault(ALWAYS) val impostorSkirtDepth: Int = 12,
    /** Extra chunk radius kept in memory beyond the streaming radius. */
    @EncodeDefault(ALWAYS) val chunkKeepMargin: Int = 2,
    /** Grace period before an out-of-range chunk is unloaded (covers a reconnect). */
    @EncodeDefault(ALWAYS) val chunkUnloadGraceSeconds: Int = 120,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PlayerSection(
    @EncodeDefault(ALWAYS) val heightStanding: Float = 2.1f,
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
data class ChunkSection(
    @EncodeDefault(ALWAYS) val transport: String = "websocket",
    @EncodeDefault(ALWAYS) val httpWorkers: Int = 4,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NetworkSection(
    @EncodeDefault(ALWAYS) val messageEncoder: String = "protobuf",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FactionsSection(
    @EncodeDefault(ALWAYS) val enabled: Boolean = false,
    @EncodeDefault(ALWAYS) val friendlyFire: Boolean = false,
    @EncodeDefault(ALWAYS) val changeCooldownSeconds: Long = 0,
    @EncodeDefault(ALWAYS) val spawnRingRadius: Double = 384.0,
    @EncodeDefault(ALWAYS)
    @org.micoli.micraft.schema.JsonSchemaConstraint(minItems = 0, maxItems = 5)
    val list: List<org.micoli.micraft.social.FactionDefinition> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonSchemaRoot(file = "server.schema.json")
data class ServerConfig(
    @EncodeDefault(ALWAYS) val world: WorldSection = WorldSection(),
    @EncodeDefault(ALWAYS) val player: PlayerSection = PlayerSection(),
    @EncodeDefault(ALWAYS) val auth: AuthSection = AuthSection(),
    @EncodeDefault(ALWAYS) val chunks: ChunkSection = ChunkSection(),
    @EncodeDefault(ALWAYS) val network: NetworkSection = NetworkSection(),
    @EncodeDefault(ALWAYS) val game: GameConfig = GameConfig(),
    @EncodeDefault(ALWAYS) val factions: FactionsSection = FactionsSection(),
)

fun loadServerConfig(
    path: Path = ConfigPaths.dataConfig("server.yaml"),
    resourcesPath: Path = ConfigPaths.resourcesConfig("server.yaml"),
): ServerConfig = loadOverridableConfig("server.yaml", OverridablePaths(resourcesPath, path))

fun applyServerConfig(config: ServerConfig) {
    with(config.world) {
        WorldConstants.WORLD_MIN_Y = worldMinY
        WorldConstants.WORLD_MAX_Y = worldMaxY
        WorldConstants.CHUNK_SIZE = chunkSize
        WorldConstants.VIEW_RADIUS = viewRadius
        WorldConstants.FORWARD_VIEW_RADIUS = forwardViewRadius
        WorldConstants.WATER_LEVEL = waterLevel
        WorldConstants.IMPOSTOR_SKIRT_DEPTH = impostorSkirtDepth
        WorldConstants.CHUNK_KEEP_MARGIN = chunkKeepMargin
        WorldConstants.CHUNK_UNLOAD_GRACE_SECONDS = chunkUnloadGraceSeconds
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
    MessageEncoding.current = MessageEncoding.fromConfigValue(config.network.messageEncoder)
    applyGameConfig(config.game)
    serverConfigLog.info(
        "Server config applied: world={}, player={}, messageEncoder={}",
        config.world,
        config.player,
        config.network.messageEncoder,
    )
}
