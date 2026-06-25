package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoadConfigLoaderTest {

    private fun configFrom(yaml: String): RoadConfig {
        val tmp = createTempFile(suffix = ".yaml")
        tmp.toFile().deleteOnExit()
        tmp.writeText(yaml)
        return loadRoadConfig(tmp)
    }

    @Test
    fun validYaml_loadsEnabledAndDefaults() {
        val config =
            configFrom(
                """
            enabled: true
            defaultRoad:
              width: 4
              surface: GRAVEL
            biomes: {}
            """
                    .trimIndent())
        assertTrue(config.enabled)
        assertEquals(4, config.defaultRoad.width)
        assertEquals(BlockType.GRAVEL, config.defaultRoad.surface)
    }

    @Test
    fun validYaml_loadsVegetationConstraints() {
        val config =
            configFrom(
                """
            enabled: true
            vegetationAllowedOnRoad: true
            minVegetationDistanceFromRoad: 3
            defaultRoad:
              width: 3
              surface: GRAVEL
            biomes: {}
            """
                    .trimIndent())
        assertTrue(config.vegetationAllowedOnRoad)
        assertEquals(3, config.minVegetationDistanceFromRoad)
    }

    @Test
    fun validYaml_loadsVoronoiAndDisplacementParams() {
        val config =
            configFrom(
                """
            enabled: true
            voronoiCellSize: 64
            displacementScale: 15.0
            displacementFrequency: 0.03
            defaultRoad:
              width: 3
              surface: GRAVEL
            biomes: {}
            """
                    .trimIndent())
        assertEquals(64, config.voronoiCellSize)
        assertEquals(15.0, config.displacementScale)
        assertEquals(0.03, config.displacementFrequency)
    }

    @Test
    fun validYaml_loadsBiomeOverrides() {
        val config =
            configFrom(
                """
            enabled: true
            defaultRoad:
              width: 3
              surface: GRAVEL
              roadProbability: 0.9
            biomes:
              desert:
                width: 5
                surface: SANDSTONE
                roadProbability: 0.3
            """
                    .trimIndent())
        val desert = config.configFor("desert")
        assertEquals(5, desert.width)
        assertEquals(BlockType.SANDSTONE, desert.surface)
        assertEquals(0.3, desert.roadProbability)
        assertEquals(0.9, config.defaultRoad.roadProbability)
    }

    @Test
    fun missingFile_createsDefaultAndReturnsDefaults() {
        val dir = createTempDirectory()
        dir.toFile().deleteOnExit()
        val path = dir.resolve("roads.yaml")
        val config = loadRoadConfig(path)
        assertTrue(path.toFile().exists(), "File should be created")
        assertTrue(config.enabled)
        assertEquals(BlockType.GRAVEL, config.defaultRoad.surface)
    }

    @Test
    fun unknownBiome_fallsBackToDefault() {
        val config =
            configFrom(
                """
            enabled: true
            defaultRoad:
              width: 3
              surface: GRAVEL
            biomes:
              forest:
                width: 2
                surface: DIRT
            """
                    .trimIndent())
        assertEquals(BlockType.GRAVEL, config.surfaceFor("unknown_biome"))
        assertEquals(BlockType.DIRT, config.surfaceFor("forest"))
    }

    @Test
    fun disabledConfig_defaultsUsed() {
        val config =
            configFrom(
                """
            enabled: false
            defaultRoad:
              width: 3
              surface: GRAVEL
            biomes: {}
            """
                    .trimIndent())
        assertFalse(config.enabled)
    }
}
