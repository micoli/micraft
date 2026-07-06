package org.micoli.micraft.world

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VegetationConfigTest {

    @Test
    fun missingFile_createsWithDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("vegetation.yaml")
        val config = VegetationConfig(path)
        assertTrue(path.toFile().exists(), "vegetation.yaml should be created")
        assertTrue(config.data.chains.isNotEmpty())
        assertTrue(path.readText().startsWith("# yaml-language-server:"))
    }

    @Test
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("vegetation.yaml")
        path.writeText("enabled: true\n")
        VegetationConfig(path)
        val written = path.readText()
        assertTrue(
            written.contains("growthCheckIntervalTicks"),
            "Missing keys must be written back to file")
        assertTrue(written.contains("oak_growth"), "Missing curated chains must be commented in")
    }

    @Test
    fun corruptFile_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("vegetation.yaml")
        path.writeText("this: [is: not: valid yaml: }")
        val config = VegetationConfig(path)
        assertTrue(config.data.chains.isNotEmpty())
    }

    @Test
    fun reload_isIdempotent_doesNotDuplicateComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("vegetation.yaml")
        path.writeText("enabled: true\n")
        val config = VegetationConfig(path)
        val afterFirstLoad = path.readText()
        config.reload()
        val afterSecondLoad = path.readText()
        assertEquals(
            afterFirstLoad, afterSecondLoad, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("growthCheckIntervalTicks").findAll(afterSecondLoad).count())
    }
}
