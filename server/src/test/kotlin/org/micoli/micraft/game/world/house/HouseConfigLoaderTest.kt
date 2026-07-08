package org.micoli.micraft.game.world.house

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HouseConfigLoaderTest {

    private val defaultYaml =
        """
        enabled: true
        gridCellSize: 48
        houseTypes:
          - id: cabin
            widthMin: 5
            widthMax: 8
            depthMin: 5
            depthMax: 8
            floorsMin: 1
            floorsMax: 1
            roofTypes: [gabled]
            roomsMin: 2
            roomsMax: 4
            doorsMin: 1
            doorsMax: 1
        biomes:
          plains:
            wallBlock: OAK_LOG
            roofBlock: OAK_LOG
            floorBlock: DIRT
            houseProbability: 0.15
            clusterBonus: 0.25
            typeRates: { cabin: 1.0 }
        """
            .trimIndent()

    private fun defaultResourcesFile(dir: Path) =
        dir.resolve("houses-defaults.yaml").apply { writeText(defaultYaml) }

    @Test
    fun missingFile_createsWithDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("houses.yaml")
        val config = loadHouseConfig(path, defaultResourcesFile(dir))
        assertTrue(path.toFile().exists(), "houses.yaml should be created")
        assertTrue(config.enabled)
        assertTrue(config.houseTypes.isNotEmpty())
        assertTrue(config.biomes.containsKey("plains"))
    }

    @Test
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("houses.yaml")
        path.writeText("enabled: true\n")
        loadHouseConfig(path, defaultResourcesFile(dir))
        val written = path.readText()
        assertTrue(written.contains("gridCellSize"), "Missing keys must be written back to file")
        assertTrue(written.contains("cabin"), "Missing curated houseTypes must be commented in")
    }

    @Test
    fun missingField_isMergedFromResourcesDefault() {
        val dir = createTempDirectory()
        val path = dir.resolve("houses.yaml")
        val resources = defaultResourcesFile(dir)
        path.writeText("enabled: true\n")
        val config = loadHouseConfig(path, resources)
        assertEquals(48, config.gridCellSize, "absent field is active, sourced from resources")
        assertTrue(
            config.biomes.containsKey("plains"), "absent map is active, sourced from resources")
    }

    @Test
    fun corruptFile_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("houses.yaml")
        path.writeText("this: [is: not: valid yaml: }")
        val config = loadHouseConfig(path, defaultResourcesFile(dir))
        assertTrue(config.enabled)
        assertTrue(config.houseTypes.isNotEmpty())
    }

    @Test
    fun corruptFile_leftUntouched() {
        val dir = createTempDirectory()
        val path = dir.resolve("houses.yaml")
        val original = "this: [is: not: valid yaml: }"
        path.writeText(original)
        loadHouseConfig(path, defaultResourcesFile(dir))
        assertEquals(original, path.readText(), "Unparseable file must not be rewritten")
    }

    @Test
    fun reload_isIdempotent_doesNotDuplicateComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("houses.yaml")
        val resources = defaultResourcesFile(dir)
        path.writeText("enabled: true\n")
        loadHouseConfig(path, resources)
        val afterFirstLoad = path.readText()
        loadHouseConfig(path, resources)
        val afterSecondLoad = path.readText()
        assertEquals(
            afterFirstLoad, afterSecondLoad, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("gridCellSize").findAll(afterSecondLoad).count())
    }

    @Test
    fun productionResourcesFile_parsesSuccessfully() {
        val projectRoot = Path.of(System.getProperty("projectDir", ".."))
        val path = projectRoot.resolve("resources/config/houses.yaml")
        assertTrue(path.toFile().exists(), "resources/config/houses.yaml should exist")
        val config = Yaml.default.decodeFromString(HouseConfig.serializer(), path.readText())
        assertTrue(config.houseTypes.isNotEmpty())
        assertTrue(config.biomes.isNotEmpty())
    }
}
