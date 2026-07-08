package org.micoli.micraft.game.world.biome

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BiomeConfigLoaderTest {

    private val defaultYaml =
        """
        voronoiCellSize: 256
        voronoiBlendRadius: 20
        biomes:
          - id: plains
            zones:
              - moistureMin: 0.0
                moistureMax: 1.0
            surface: GRASS
            subsurface: DIRT
        """
            .trimIndent()

    private fun defaultResourcesFile(dir: Path) =
        dir.resolve("biomes-defaults.yaml").apply { writeText(defaultYaml) }

    @Test
    fun missingFile_createsWithDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("biomes.yaml")
        val registry = loadBiomeRegistry(path, defaultResourcesFile(dir))
        assertTrue(path.toFile().exists(), "biomes.yaml should be created")
        assertTrue(registry.biomes.isNotEmpty())
        assertTrue(registry.biomes.any { it.id == "plains" })
    }

    @Test
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("biomes.yaml")
        path.writeText("voronoiCellSize: 300\n")
        loadBiomeRegistry(path, defaultResourcesFile(dir))
        val written = path.readText()
        assertTrue(
            written.contains("voronoiCellSize: 300"), "existing key untouched with its value")
        assertTrue(written.contains("# biomes:"), "missing curated biomes must be commented in")
        assertTrue(written.contains("plains"), "missing curated biomes must be commented in")
    }

    @Test
    fun missingField_isMergedFromResourcesDefault() {
        val dir = createTempDirectory()
        val path = dir.resolve("biomes.yaml")
        val resources = defaultResourcesFile(dir)
        path.writeText("voronoiCellSize: 300\n")
        val registry = loadBiomeRegistry(path, resources)
        assertTrue(
            registry.biomes.any { it.id == "plains" },
            "absent field is active, sourced from resources")
        assertEquals(20, registry.voronoiBlendRadius)
    }

    @Test
    fun corruptFile_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("biomes.yaml")
        path.writeText("this: [is: not: valid yaml: }")
        val registry = loadBiomeRegistry(path, defaultResourcesFile(dir))
        assertTrue(registry.biomes.isNotEmpty())
    }

    @Test
    fun corruptFile_leftUntouched() {
        val dir = createTempDirectory()
        val path = dir.resolve("biomes.yaml")
        val original = "this: [is: not: valid yaml: }"
        path.writeText(original)
        loadBiomeRegistry(path, defaultResourcesFile(dir))
        assertEquals(original, path.readText(), "Unparseable file must not be rewritten")
    }

    @Test
    fun reload_isIdempotent_doesNotDuplicateComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("biomes.yaml")
        val resources = defaultResourcesFile(dir)
        path.writeText("voronoiCellSize: 300\n")
        loadBiomeRegistry(path, resources)
        val afterFirstLoad = path.readText()
        loadBiomeRegistry(path, resources)
        val afterSecondLoad = path.readText()
        assertEquals(
            afterFirstLoad, afterSecondLoad, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("voronoiBlendRadius").findAll(afterSecondLoad).count())
    }

    @Test
    fun productionResourcesFile_parsesSuccessfully() {
        val projectRoot = Path.of(System.getProperty("projectDir", ".."))
        val path = projectRoot.resolve("resources/config/biomes.yaml")
        assertTrue(path.toFile().exists(), "resources/config/biomes.yaml should exist")
        val config = Yaml.default.decodeFromString(BiomeConfig.serializer(), path.readText())
        assertTrue(config.biomes.isNotEmpty())
    }
}
