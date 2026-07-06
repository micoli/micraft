package org.micoli.micraft.combat

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class CombatConfigTest {
    private fun resourcesDefault(): java.nio.file.Path {
        val dir = Files.createTempDirectory("combat-config-resources")
        val path = dir.resolve("combat.yaml")
        path.writeText("maxCombatRange: 10.0\ndowningRollIntervalMs: 3000\nmaxRage: 100\n")
        return path
    }

    @Test
    fun missingFile_returnsDefaults() {
        val dir = Files.createTempDirectory("combat-config-test")
        val path = dir.resolve("combat.yaml")
        val config = CombatConfig(path, resourcesDefault()).data
        assertEquals(10.0f, config.maxCombatRange)
        assertEquals(3000L, config.downingRollIntervalMs)
        assertEquals(100, config.maxRage)
    }

    @Test
    fun validYaml_loadsValues() {
        val dir = Files.createTempDirectory("combat-config-test2")
        val path = dir.resolve("combat.yaml")
        path.writeText("maxCombatRange: 15.0\ndowningRollIntervalMs: 5000\nmaxRage: 200\n")
        val config = CombatConfig(path, resourcesDefault()).data
        assertEquals(15.0f, config.maxCombatRange)
        assertEquals(5000L, config.downingRollIntervalMs)
        assertEquals(200, config.maxRage)
    }

    @Test
    fun invalidYaml_fallsBackToDefaults() {
        val dir = Files.createTempDirectory("combat-config-test3")
        val path = dir.resolve("combat.yaml")
        path.writeText("this: [is: broken}")
        val config = CombatConfig(path, resourcesDefault()).data
        assertEquals(10.0f, config.maxCombatRange)
        assertEquals(100, config.maxRage)
    }

    @Test
    fun partialYaml_usesDefaultsForMissingFields() {
        val dir = Files.createTempDirectory("combat-config-test4")
        val path = dir.resolve("combat.yaml")
        path.writeText("maxCombatRange: 20.0\n")
        val config = CombatConfig(path, resourcesDefault()).data
        assertEquals(20.0f, config.maxCombatRange)
        assertEquals(3000L, config.downingRollIntervalMs)
        assertEquals(100, config.maxRage)
    }
}
