package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.micoli.micraft.world.proceduralGenerator.road.RoadConfig

class RoadConfigLoaderTest {

    private val defaultYaml =
        """
        enabled: true
        defaultRoad:
          width: 3
          surface: GRAVEL
          roadProbability: 0.7
        biomes:
          desert:
            width: 5
            surface: SANDSTONE
            roadProbability: 0.5
        """
            .trimIndent()

    private fun defaultResourcesFile(dir: Path) =
        dir.resolve("roads-defaults.yaml").apply { writeText(defaultYaml) }

    private fun configFrom(yaml: String): RoadConfig {
        val dir = createTempDirectory()
        val tmp = createTempFile(dir, suffix = ".yaml")
        tmp.writeText(yaml)
        return loadRoadConfig(tmp, defaultResourcesFile(dir))
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
        val path = dir.resolve("roads.yaml")
        val config = loadRoadConfig(path, defaultResourcesFile(dir))
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
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("roads.yaml")
        path.writeText("enabled: true\n")
        loadRoadConfig(path, defaultResourcesFile(dir))
        val written = path.readText()
        assertTrue(written.contains("voronoiCellSize"), "Missing keys must be written back to file")
        assertTrue(written.contains("# biomes:"), "Missing curated biomes must be commented in")
        assertTrue(written.contains("desert"), "Missing curated biomes must be commented in")
    }

    @Test
    fun missingField_isMergedFromResourcesDefault() {
        val dir = createTempDirectory()
        val path = dir.resolve("roads.yaml")
        val resources = defaultResourcesFile(dir)
        path.writeText("enabled: true\n")
        val config = loadRoadConfig(path, resources)
        assertEquals(3, config.defaultRoad.width, "absent field is active, sourced from resources")
        assertTrue(
            config.biomes.containsKey("desert"), "absent map is active, sourced from resources")
    }

    @Test
    fun corruptFile_leftUntouched() {
        val dir = createTempDirectory()
        val path = dir.resolve("roads.yaml")
        val original = "this: [is: not: valid yaml: }"
        path.writeText(original)
        loadRoadConfig(path, defaultResourcesFile(dir))
        assertEquals(original, path.readText(), "Unparseable file must not be rewritten")
    }

    @Test
    fun reload_isIdempotent_doesNotDuplicateComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("roads.yaml")
        val resources = defaultResourcesFile(dir)
        path.writeText("enabled: true\n")
        loadRoadConfig(path, resources)
        val afterFirstLoad = path.readText()
        loadRoadConfig(path, resources)
        val afterSecondLoad = path.readText()
        assertEquals(
            afterFirstLoad, afterSecondLoad, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("voronoiCellSize").findAll(afterSecondLoad).count())
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

    @Test
    fun productionResourcesFile_parsesSuccessfully() {
        val projectRoot = Path.of(System.getProperty("projectDir", ".."))
        val path = projectRoot.resolve("resources/config/roads.yaml")
        assertTrue(path.toFile().exists(), "resources/config/roads.yaml should exist")
        val config = Yaml.default.decodeFromString(RoadConfig.serializer(), path.readText())
        assertTrue(config.biomes.isNotEmpty())
    }
}
