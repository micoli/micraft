package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerConfigLoaderTest {

    @Test
    fun missingFile_createsWithDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        val config = loadServerConfig(path)
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
            gameplay:
              gravity: -25.0
              spawnY: 64.0
            """
                .trimIndent())
        val config = loadServerConfig(path)
        assertEquals(5, config.world.viewRadius)
        assertEquals(10, config.world.forwardViewRadius)
        assertEquals(6.0f, config.player.speedStanding)
        assertEquals(-25.0f, config.gameplay.gravity)
        assertEquals(64.0f, config.gameplay.spawnY)
    }

    @Test
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        path.writeText("world:\n  viewRadius: 4\n")
        loadServerConfig(path)
        val written = path.readText()
        assertTrue(written.contains("worldMaxY"), "Missing keys must be written back to file")
        assertTrue(
            written.contains("speedStanding"), "Missing sections must be written back to file")
    }

    @Test
    fun corruptFile_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("server.yaml")
        path.writeText("this: [is: not: valid yaml: }")
        val config = loadServerConfig(path)
        assertEquals(ServerConfig(), config)
    }

    @Test
    fun applyServerConfig_setsWorldConstants() {
        val config =
            ServerConfig(
                world = WorldSection(viewRadius = 5, forwardViewRadius = 9, worldMaxY = 512),
                player = PlayerSection(speedStanding = 6.0f, width = 0.8f),
                gameplay =
                    GameplaySection(
                        gravity = -15f,
                        tickMs = 100L,
                        saveIntervalSeconds = 60,
                        spawnX = 16f,
                        spawnY = 64f,
                        spawnZ = 16f),
            )
        applyServerConfig(config)
        assertEquals(5, WorldConstants.VIEW_RADIUS)
        assertEquals(9, WorldConstants.FORWARD_VIEW_RADIUS)
        assertEquals(9, WorldConstants.CLIENT_VIEW_RADIUS)
        assertEquals(512, WorldConstants.WORLD_MAX_Y)
        assertEquals(6.0f, PlayerConstants.SPEED_STANDING)
        assertEquals(0.8f, PlayerConstants.WIDTH)
        assertEquals(-15f, org.micoli.micraft.GRAVITY)
        assertEquals(100L, org.micoli.micraft.TICK_MS)
        assertEquals(600, org.micoli.micraft.SAVE_INTERVAL_TICKS)
    }
}
