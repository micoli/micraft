package org.micoli.micraft.npc

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpcConfigLoaderTest {
    @Test
    fun missingFile_createsDefaultAndReturnsDefaults() {
        val dir = Files.createTempDirectory("npc-config-test")
        val path = dir.resolve("npc.yaml")
        val loader = NpcConfigLoader(path)
        assertTrue(path.toFile().exists(), "Should create default file")
        val config = loader.load()
        assertEquals(40, config.wanderPauseTicksMin)
        assertEquals(120, config.wanderPauseTicksMax)
        assertEquals(60, config.wanderStepTicksMax)
        assertEquals(4f, config.interactionRange)
        assertEquals(96f, config.updateRange)
    }

    @Test
    fun validYaml_loadsCustomValues() {
        val dir = Files.createTempDirectory("npc-config-test2")
        val path = dir.resolve("npc.yaml")
        path.writeText(
            """
            wanderPauseTicksMin: 10
            wanderPauseTicksMax: 30
            wanderStepTicksMax: 20
            interactionRange: 8.0
            updateRange: 64.0
            spawnCheckIntervalTicks: 100
            maxSpawnAttemptsPerTick: 5
            jumpVelocity: 7.0
            """
                .trimIndent())
        val config = NpcConfigLoader(path).load()
        assertEquals(10, config.wanderPauseTicksMin)
        assertEquals(30, config.wanderPauseTicksMax)
        assertEquals(8.0f, config.interactionRange)
        assertEquals(7.0f, config.jumpVelocity)
    }

    @Test
    fun invalidYaml_returnsDefaults() {
        val dir = Files.createTempDirectory("npc-config-test3")
        val path = dir.resolve("npc.yaml")
        path.writeText("this: [is: broken}")
        val config = NpcConfigLoader(path).load()
        assertEquals(40, config.wanderPauseTicksMin)
    }

    @Test
    fun reload_readsUpdatedValues() {
        val dir = Files.createTempDirectory("npc-config-test4")
        val path = dir.resolve("npc.yaml")
        path.writeText(
            "wanderPauseTicksMin: 5\nwanderPauseTicksMax: 15\nwanderStepTicksMax: 10\ninteractionRange: 2.0\nupdateRange: 50.0\nspawnCheckIntervalTicks: 100\nmaxSpawnAttemptsPerTick: 2\njumpVelocity: 5.0\n")
        val loader = NpcConfigLoader(path)
        val first = loader.load()
        assertEquals(5, first.wanderPauseTicksMin)

        path.writeText(
            "wanderPauseTicksMin: 99\nwanderPauseTicksMax: 200\nwanderStepTicksMax: 80\ninteractionRange: 2.0\nupdateRange: 50.0\nspawnCheckIntervalTicks: 100\nmaxSpawnAttemptsPerTick: 2\njumpVelocity: 5.0\n")
        val second = loader.reload()
        assertEquals(99, second.wanderPauseTicksMin)
    }
}
