package org.micoli.micraft.support

import org.micoli.micraft.CommandContext
import org.micoli.micraft.player.Orientation
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.I18nConfig
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldState
import java.nio.file.Path
import java.nio.file.Paths

fun testPlayerState(
    id: String = "test-id",
    name: String = "Alice",
    pos: Vec3 = Vec3(8f, 8f, 8f),
    language: String = "en",
    flying: Boolean = false,
    stance: PlayerStance = PlayerStance.STANDING,
    speedMultiplier: Float = 1f,
) = PlayerState(
    id = id,
    name = name,
    pos = pos,
    orientation = Orientation(0f, 0f),
    stance = stance,
    flying = flying,
    speedMultiplier = speedMultiplier,
    language = language,
)

fun testSession(
    id: String = "test-id",
    name: String = "Alice",
    pos: Vec3 = Vec3(8f, 8f, 8f),
    language: String = "en",
) = FakePlayerSession(id, name, testPlayerState(id = id, name = name, pos = pos, language = language))

fun testWorld(vararg solid: Triple<Int, Int, Int>): WorldState =
    WorldState(MapChunkGenerator(solid.associateWith { BlockType.STONE }))

fun testI18n(): I18nConfig {
    val url = object {}.javaClass.classLoader.getResource("i18n")
        ?: error("test i18n resource not found")
    val corePath = Paths.get(url.toURI())
    val pluginDirs = System.getProperty("projectDir")
        ?.let { Path.of(it).resolve("plugins") }
        ?.takeIf { it.toFile().exists() }
        ?.let { pluginsRoot ->
            pluginsRoot.toFile().listFiles { f -> f.isDirectory }
                ?.map { pluginsRoot.resolve(it.name).resolve("data/i18n") }
                ?.filter { it.toFile().exists() }
                ?: emptyList()
        }
        ?: emptyList()
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
    i18n: I18nConfig = testI18n(),
) = CommandContext(
    world = world,
    persistence = null,
    i18n = i18n,
    broadcast = broadcast,
    sessions = { sessions },
    kickSession = kickSession,
    reloadConfig = reloadConfig,
    savePlayer = savePlayer,
    worldItems = worldItems,
)
