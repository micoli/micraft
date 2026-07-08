package org.micoli.micraft.game.world.weather

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherConfigTest {

    @Test
    fun missingFile_createsWithDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("weather.yaml")
        val config = WeatherConfig(path)
        assertTrue(path.toFile().exists(), "weather.yaml should be created")
        assertTrue(config.data.weatherTypes.isNotEmpty())
        assertTrue(path.readText().startsWith("# yaml-language-server:"))
    }

    @Test
    fun partialFile_writesMissingKeysBack() {
        val dir = createTempDirectory()
        val path = dir.resolve("weather.yaml")
        path.writeText("enabled: true\n")
        WeatherConfig(path)
        val written = path.readText()
        assertTrue(written.contains("weatherTypes"), "Missing keys must be written back to file")
        assertTrue(written.contains("RAIN"), "Missing curated weather types must be commented in")
    }

    @Test
    fun corruptFile_fallsBackToDefaults() {
        val dir = createTempDirectory()
        val path = dir.resolve("weather.yaml")
        path.writeText("this: [is: not: valid yaml: }")
        val config = WeatherConfig(path)
        assertTrue(config.data.weatherTypes.isNotEmpty())
    }

    @Test
    fun reload_isIdempotent_doesNotDuplicateComments() {
        val dir = createTempDirectory()
        val path = dir.resolve("weather.yaml")
        path.writeText("enabled: true\n")
        val config = WeatherConfig(path)
        val afterFirstLoad = path.readText()
        config.reload()
        val afterSecondLoad = path.readText()
        assertEquals(
            afterFirstLoad, afterSecondLoad, "Reloading must not duplicate default comments")
        assertEquals(1, Regex("weatherTypes").findAll(afterSecondLoad).count())
    }
}
