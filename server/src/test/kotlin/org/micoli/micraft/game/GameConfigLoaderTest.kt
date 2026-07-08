package org.micoli.micraft.game

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GameConfigLoaderTest {

    @AfterTest
    fun resetGlobals() {
        applyGameConfig(GameConfig())
    }

    @Test
    fun applyGameConfig_setsGlobalConstantsFromConfig() {
        applyGameConfig(
            GameConfig(gravity = -9.8f, jumpSpeed = 5f, ticksPerDay = 1000L, tickMs = 25L))
        assertEquals(-9.8f, GRAVITY)
        assertEquals(5f, JUMP_SPEED)
        assertEquals(1000L, TICKS_PER_DAY)
        assertEquals(25L, TICK_MS)
    }

    @Test
    fun applyGameConfig_debugWorld_overridesSpawnPosition() {
        applyGameConfig(GameConfig(debugWorld = true, spawnX = 100f, spawnY = 5f, spawnZ = 3f))
        assertEquals(true, DEBUG_WORLD)
        assertEquals(100f, SPAWN_X)
        assertEquals(1f, SPAWN_Y)
        assertEquals(14f, SPAWN_Z)
    }

    @Test
    fun applyGameConfig_normalWorld_usesConfiguredSpawn() {
        applyGameConfig(GameConfig(debugWorld = false, spawnX = 100f, spawnY = 50f, spawnZ = 30f))
        assertEquals(100f, SPAWN_X)
        assertEquals(50f, SPAWN_Y)
        assertEquals(30f, SPAWN_Z)
    }

    @Test
    fun applyGameConfig_saveIntervalConvertedToTicks() {
        applyGameConfig(GameConfig(saveIntervalSeconds = 60, tickMs = 100L))
        assertEquals(600, SAVE_INTERVAL_TICKS)
    }
}
