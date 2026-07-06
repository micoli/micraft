package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerConfigLoaderTest {

    private fun defaultResourcesFile(dir: java.nio.file.Path) =
        dir.resolve("server-defaults.yaml").apply {
            writeText(Yaml.default.encodeToString(ServerConfig.serializer(), ServerConfig()))
        }

    @Test
    fun missingFile_createsWithDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        val config = loadServerConfig(path, defaultResourcesFile(dir))
        assertTrue(path.toFile().exists(), "server.yaml should be created")
        assertEquals(ServerConfig(), config)
    }

    @Test
    fun existingFile_loadsValues() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        path.writeText(
            """
            world:
              viewRadius: 5
              forwardViewRadius: 10
            player:
              speedStanding: 6.0
            """
                .trimIndent())
        val config = loadServerConfig(path, defaultResourcesFile(dir))
        assertEquals(5, config.world.viewRadius)
        assertEquals(10, config.world.forwardViewRadius)
        assertEquals(6.0f, config.player.speedStanding)
        assertEquals(GameConfig(), config.game, "game section falls back to defaults")
    }

    @Test
    fun existingFile_loadsGameSectionValues() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        path.writeText(
            """
            game:
              gravity: -25.0
              spawnY: 64.0
              ticksPerDay: 36000
            """
                .trimIndent())
        val config = loadServerConfig(path, defaultResourcesFile(dir))
        assertEquals(-25.0f, config.game.gravity)
        assertEquals(64.0f, config.game.spawnY)
        assertEquals(36000L, config.game.ticksPerDay)
    }

    @Test
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        path.writeText("world:\n  viewRadius: 4\n")
        loadServerConfig(path, defaultResourcesFile(dir))
        val written = path.readText()
        assertTrue(written.contains("worldMaxY"), "Missing keys must be written back to file")
        assertTrue(
            written.contains("speedStanding"), "Missing sections must be written back to file")
    }

    @Test
    fun missingField_isMergedFromResourcesDefault() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        val resources = dir.resolve("server-defaults.yaml")
        resources.writeText(
            Yaml.default.encodeToString(
                ServerConfig.serializer(), ServerConfig(world = WorldSection(waterLevel = 99))))
        path.writeText("world:\n  viewRadius: 4\n")
        val config = loadServerConfig(path, resources)
        assertEquals(99, config.world.waterLevel, "absent field is active, sourced from resources")
    }

    @Test
    fun corruptFile_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        path.writeText("this: [is: not: valid yaml: }")
        val config = loadServerConfig(path, defaultResourcesFile(dir))
        assertEquals(ServerConfig(), config)
    }

    @Test
    fun corruptFile_leftUntouched() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        val original = "this: [is: not: valid yaml: }"
        path.writeText(original)
        loadServerConfig(path, defaultResourcesFile(dir))
        assertEquals(original, path.readText(), "Unparseable file must not be rewritten")
    }

    @Test
    fun reload_isIdempotent_doesNotDuplicateComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        val resources = defaultResourcesFile(dir)
        path.writeText("world:\n  viewRadius: 4\n")
        loadServerConfig(path, resources)
        val afterFirstLoad = path.readText()
        loadServerConfig(path, resources)
        val afterSecondLoad = path.readText()
        assertEquals(
            afterFirstLoad, afterSecondLoad, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("waterLevel").findAll(afterSecondLoad).count())
    }

    @Test
    fun applyServerConfig_setsWorldConstants() {
        val config =
            ServerConfig(
                world = WorldSection(viewRadius = 5, forwardViewRadius = 9, worldMaxY = 512),
                player = PlayerSection(speedStanding = 6.0f, width = 0.8f),
            )
        applyServerConfig(config)
        assertEquals(5, WorldConstants.VIEW_RADIUS)
        assertEquals(9, WorldConstants.FORWARD_VIEW_RADIUS)
        assertEquals(9, WorldConstants.CLIENT_VIEW_RADIUS)
        assertEquals(512, WorldConstants.WORLD_MAX_Y)
        assertEquals(6.0f, PlayerConstants.SPEED_STANDING)
        assertEquals(0.8f, PlayerConstants.WIDTH)
    }

    @Test
    fun applyServerConfig_setsGameConstants() {
        val config =
            ServerConfig(
                game =
                    GameConfig(
                        gravity = -15f,
                        tickMs = 100L,
                        saveIntervalSeconds = 60,
                        spawnX = 16f,
                        ticksPerDay = 36_000L,
                        timeBroadcastTicks = 10,
                        maxInteractionDistance = 5.0,
                    ))
        applyServerConfig(config)
        assertEquals(-15f, org.micoli.micraft.GRAVITY)
        assertEquals(100L, org.micoli.micraft.TICK_MS)
        assertEquals(600, org.micoli.micraft.SAVE_INTERVAL_TICKS)
        assertEquals(16f, org.micoli.micraft.SPAWN_X)
        assertEquals(36_000L, org.micoli.micraft.TICKS_PER_DAY)
        assertEquals(10, org.micoli.micraft.TIME_BROADCAST_TICKS)
        assertEquals(5.0, org.micoli.micraft.MAX_INTERACTION_DISTANCE)
    }
}
