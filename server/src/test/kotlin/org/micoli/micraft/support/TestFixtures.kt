package org.micoli.micraft.support

import java.nio.file.Path
import java.nio.file.Paths
import org.micoli.micraft.CommandContext
import org.micoli.micraft.ConfigRegistry
import org.micoli.micraft.npc.NpcManager
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.tick.LiquidManager
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.I18nConfig
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldState

fun testPlayerState(
    id: String = "test-id",
    name: String = "Alice",
    pos: Vec3 = Vec3(8f, 8f, 8f),
    orientation: Orientation = Orientation(0f, 0f),
    language: String = "en",
    flying: Boolean = false,
    stance: PlayerStance = PlayerStance.STANDING,
    speedMultiplier: Float = 1f,
    shadersEnabled: Boolean = true,
) =
    PlayerState(
        id = id,
        name = name,
        pos = pos,
        orientation = orientation,
        stance = stance,
        flying = flying,
        speedMultiplier = speedMultiplier,
        language = language,
        shadersEnabled = shadersEnabled,
    )

fun testSession(
    id: String = "test-id",
    name: String = "Alice",
    pos: Vec3 = Vec3(8f, 8f, 8f),
    orientation: Orientation = Orientation(0f, 0f),
    language: String = "en",
    shadersEnabled: Boolean = true,
) =
    FakePlayerSession(
        id,
        name,
        testPlayerState(
            id = id,
            name = name,
            pos = pos,
            orientation = orientation,
            language = language,
            shadersEnabled = shadersEnabled,
        ))

fun testWorld(vararg solid: Triple<Int, Int, Int>): WorldState {
    val world = WorldState(MapChunkGenerator(solid.associateWith { BlockType.STONE }))
    // Pre-generate referenced chunks so getBlockIfLoaded works in NPC physics tests
    solid
        .map { (x, _, z) ->
            ChunkPos(
                Math.floorDiv(x, WorldConstants.CHUNK_SIZE),
                Math.floorDiv(z, WorldConstants.CHUNK_SIZE))
        }
        .toSet()
        .forEach { world.getOrGenerate(it) }
    return world
}

fun testI18n(): I18nConfig {
    val url =
        object {}.javaClass.classLoader.getResource("i18n") ?: error("test i18n resource not found")
    val corePath = Paths.get(url.toURI())
    val pluginDirs =
        System.getProperty("projectDir")
            ?.let { Path.of(it).resolve("plugins") }
            ?.takeIf { it.toFile().exists() }
            ?.let { pluginsRoot ->
                pluginsRoot
                    .toFile()
                    .listFiles { f -> f.isDirectory }
                    ?.map { pluginsRoot.resolve(it.name).resolve("data/i18n") }
                    ?.filter { it.toFile().exists() } ?: emptyList()
            } ?: emptyList()
    return I18nConfig(listOf(corePath) + pluginDirs)
}

fun testContext(
    world: WorldState = testWorld(),
    sessions: List<PlayerSession> = emptyList(),
    broadcast: suspend (ServerMessage) -> Unit = {},
    kickSession: suspend (String) -> Unit = {},
    reloadConfig: (suspend () -> String)? = null,
    savePlayer: (PlayerSession) -> Unit = {},
    worldItems: WorldItemManager? = null,
    npcManager: NpcManager? = null,
    i18n: I18nConfig = testI18n(),
    getGameTime: () -> Long = { 0L },
    setGameTime: (Long) -> Unit = {},
    refetchChunks: (suspend (org.micoli.micraft.session.PlayerSession) -> Unit)? = null,
    liquidManager: LiquidManager? = null,
    configRegistry: ConfigRegistry? = null,
) =
    CommandContext(
        world = world,
        persistence = null,
        i18n = i18n,
        broadcast = broadcast,
        sessions = { sessions },
        kickSession = kickSession,
        reloadConfig = reloadConfig,
        savePlayer = savePlayer,
        worldItems = worldItems,
        npcManager = npcManager,
        getGameTime = getGameTime,
        setGameTime = setGameTime,
        refetchChunks = refetchChunks,
        liquidManager = liquidManager,
        configRegistry = configRegistry,
    )
