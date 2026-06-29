package org.micoli.micraft.world

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith

class YamlSchemaValidatorTest {

    private val dataDir: Path = Path.of(System.getProperty("projectDir", ".")).resolve("data")

    private fun tempSchema(content: String): java.net.URI {
        val f = Files.createTempFile("micraft-schema-test", ".json")
        f.toFile().writeText(content)
        return f.toUri()
    }

    @Test
    fun `valid YAML passes`() {
        val schema =
            tempSchema(
                """{"$${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""")
        val yaml =
            Files.createTempFile("micraft-yaml-test", ".yaml").also {
                it.toFile().writeText("name: hello")
            }
        validateYaml(yaml, schema)
    }

    @Test
    fun `invalid YAML throws`() {
        val schema =
            tempSchema(
                """{"$${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""")
        val yaml =
            Files.createTempFile("micraft-yaml-test", ".yaml").also {
                it.toFile().writeText("age: 42")
            }
        assertFailsWith<IllegalStateException> { validateYaml(yaml, schema) }
    }

    @Test
    fun `missing YAML is silently skipped`() {
        val schema =
            tempSchema(
                """{"$${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object"}""")
        validateYaml(Path.of("/tmp/micraft-nonexistent-${System.nanoTime()}.yaml"), schema)
    }

    @Test
    fun `all project YAML configs pass their schemas`() {
        validateAllYamlConfigs(dataDir.resolve("config"))
    }
}
