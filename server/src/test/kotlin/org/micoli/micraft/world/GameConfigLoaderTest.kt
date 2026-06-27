package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameConfigLoaderTest {

    @Test
    fun missingFile_createsWithDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("game.yaml")
        val config = loadGameConfig(path)
        assertTrue(path.toFile().exists(), "game.yaml should be created")
        assertEquals(GameConfig(), config)
    }

    @Test
    fun existingFile_loadsValues() {
        val dir = createTempDirectory()
        val path = dir.resolve("game.yaml")
        path.writeText(
            """
            gravity: -25.0
            spawnY: 64.0
            ticksPerDay: 36000
            maxInteractionDistance: 5.0
            """
                .trimIndent())
        val config = loadGameConfig(path)
        assertEquals(-25.0f, config.gravity)
        assertEquals(64.0f, config.spawnY)
        assertEquals(36000L, config.ticksPerDay)
        assertEquals(5.0, config.maxInteractionDistance)
    }

    @Test
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("game.yaml")
        path.writeText("gravity: -15.0\n")
        loadGameConfig(path)
        val written = path.readText()
        assertTrue(written.contains("tickMs"), "Missing keys must be written back to file")
        assertTrue(written.contains("spawnX"), "Missing keys must be written back to file")
    }

    @Test
    fun corruptFile_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("game.yaml")
        path.writeText("this: [is: not: valid yaml: }")
        val config = loadGameConfig(path)
        assertEquals(GameConfig(), config)
    }

    @Test
    fun applyGameConfig_setsGameConstants() {
        val config =
            GameConfig(
                gravity = -15f,
                tickMs = 100L,
                saveIntervalSeconds = 60,
                spawnX = 16f,
                ticksPerDay = 36_000L,
                timeBroadcastTicks = 10,
                maxInteractionDistance = 5.0,
            )
        applyGameConfig(config)
        assertEquals(-15f, org.micoli.micraft.GRAVITY)
        assertEquals(100L, org.micoli.micraft.TICK_MS)
        assertEquals(600, org.micoli.micraft.SAVE_INTERVAL_TICKS)
        assertEquals(16f, org.micoli.micraft.SPAWN_X)
        assertEquals(36_000L, org.micoli.micraft.TICKS_PER_DAY)
        assertEquals(10, org.micoli.micraft.TIME_BROADCAST_TICKS)
        assertEquals(5.0, org.micoli.micraft.MAX_INTERACTION_DISTANCE)
    }
}
