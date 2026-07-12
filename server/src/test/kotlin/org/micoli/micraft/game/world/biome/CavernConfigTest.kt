package org.micoli.micraft.game.world.biome

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.BlockType

class CavernConfigTest {

    private fun loadRegistryFrom(yaml: String): BiomeRegistry {
        val dir = createTempDirectory()
        val resources = dir.resolve("biomes-defaults.yaml").apply { writeText(yaml) }
        return loadBiomeRegistry(dir.resolve("biomes.yaml"), resources)
    }

    @Test
    fun biomeWithNoCavernField_hasNullCaverns() {
        val registry =
            loadRegistryFrom(
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
                    .trimIndent())
        assertNull(registry.biomes.first().caverns, "absent caverns field should be null")
    }

    @Test
    fun cavernConfig_parsesAllFields() {
        val registry =
            loadRegistryFrom(
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
                caverns:
                  cavernMinHeight: 10
                  cavernMaxHeight: 80
                  stalactitesPresent: true
                  stalagmitesPresent: false
                  wallBlock: STONE
            """
                    .trimIndent())
        val caverns = registry.biomes.first { it.id == "plains" }.caverns
        assertNotNull(caverns)
        assertEquals(10, caverns.cavernMinHeight)
        assertEquals(80, caverns.cavernMaxHeight)
        assertTrue(caverns.stalactitesPresent)
        assertTrue(!caverns.stalagmitesPresent)
        assertEquals(BlockType.STONE, caverns.wallBlock)
    }

    @Test
    fun cavernConfig_defaults_areReasonable() {
        val c = CavernConfig()
        assertTrue(c.cavernMinHeight >= 0)
        assertTrue(c.cavernMaxHeight > c.cavernMinHeight)
        assertNotNull(c.wallBlock)
        assertTrue(c.cavernMinRadius >= 1)
        assertTrue(c.cavernMaxRadius >= c.cavernMinRadius)
    }

    @Test
    fun cavernConfig_wallBlockSelectFiller_respectsDensityWeights() {
        // STONE has 90% weight → hash 0.0 picks STONE, hash 0.99 picks SANDSTONE
        val biome =
            BiomeDefinition(
                id = "test",
                zones = listOf(BiomeZone(0.0, 1.0)),
                surface = BlockType.GRASS,
                subsurface = BlockType.DIRT,
                fillers =
                    listOf(
                        FillerEntry(BlockType.STONE, 9.0), FillerEntry(BlockType.SANDSTONE, 1.0)),
            )
        assertEquals(BlockType.STONE, biome.selectFiller(0.0))
        assertEquals(BlockType.SANDSTONE, biome.selectFiller(0.99))
    }

    @Test
    fun productionBiomesYaml_allBiomesHaveCavernConfig() {
        val projectRoot = Path.of(System.getProperty("projectDir", ".."))
        val resourcesPath = projectRoot.resolve("resources/config/biomes.yaml")
        assertTrue(resourcesPath.toFile().exists())
        val config =
            Yaml.default.decodeFromString(BiomeConfig.serializer(), resourcesPath.readText())
        val missing = config.biomes.filter { it.caverns == null }.map { it.id }
        assertTrue(missing.isEmpty(), "All biomes in production yaml should have caverns: $missing")
    }

    @Test
    fun productionBiomesYaml_cavernHeightRangesAreValid() {
        val projectRoot = Path.of(System.getProperty("projectDir", ".."))
        val config =
            Yaml.default.decodeFromString(
                BiomeConfig.serializer(),
                projectRoot.resolve("resources/config/biomes.yaml").readText())
        for (biome in config.biomes) {
            val c = biome.caverns ?: continue
            assertTrue(c.cavernMinHeight >= 0, "${biome.id}: cavernMinHeight must be >= 0")
            assertTrue(
                c.cavernMaxHeight > c.cavernMinHeight,
                "${biome.id}: cavernMaxHeight must be > cavernMinHeight")
            assertNotNull(c.wallBlock, "${biome.id}: wallBlock must not be null")
            assertTrue(c.cavernMinRadius >= 1, "${biome.id}: cavernMinRadius must be >= 1")
            assertTrue(
                c.cavernMaxRadius >= c.cavernMinRadius,
                "${biome.id}: cavernMaxRadius must be >= cavernMinRadius")
        }
    }
}
