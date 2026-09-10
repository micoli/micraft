package org.micoli.micraft.config

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.world.weather.WeatherConfigData

class SchemaValidationTest {

    private fun tmpYaml(content: String) =
        createTempDirectory().resolve("c.yaml").apply { writeText(content) }

    @Test
    fun missingFile_isSkipped() {
        val path = createTempDirectory().resolve("absent.yaml")
        assertNull(SchemaValidation.errors(path, "weather.schema.json"))
    }

    @Test
    fun emptyFile_isSkipped() {
        assertNull(SchemaValidation.errors(tmpYaml("\n\n"), "weather.schema.json"))
    }

    @Test
    fun unknownSchema_isSkipped() {
        assertNull(SchemaValidation.errors(tmpYaml("a: 1"), "does-not-exist.schema.json"))
    }

    @Test
    fun validDocument_hasNoErrors() {
        val errors = SchemaValidation.errors(tmpYaml("enabled: true\n"), "weather.schema.json")
        assertEquals(emptyList(), errors)
    }

    @Test
    fun invalidDocument_reportsErrors() {
        val errors =
            SchemaValidation.errors(tmpYaml("enabled: \"not-a-bool\"\n"), "weather.schema.json")
        assertTrue(errors != null && errors.isNotEmpty(), "expected schema violations, got $errors")
    }

    @Test
    fun schemaFileOf_readsAnnotation() {
        assertEquals("weather.schema.json", schemaFileOf(WeatherConfigData::class))
    }

    @Test
    fun validate_nonStrict_neverThrows() {
        // Default env: no MICRAFT_CONFIG_STRICT -> invalid config only warns.
        SchemaValidation.validate(tmpYaml("enabled: 123\n"), "weather.schema.json")
    }
}
