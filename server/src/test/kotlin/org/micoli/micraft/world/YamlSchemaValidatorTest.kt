package org.micoli.micraft.world

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith

class YamlSchemaValidatorTest {

    private val dataDir: Path = Path.of(System.getProperty("projectDir", ".")).resolve("data")

    @Test
    fun `valid YAML passes`() {
        val tmpDir = Files.createTempDirectory("micraft-schema-test")
        val schemasDir = tmpDir.resolve("schemas").also { Files.createDirectories(it) }
        val schema =
            schemasDir.resolve("test.schema.json").also {
                it.toFile()
                    .writeText(
                        """{"$${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""")
            }
        val yaml = tmpDir.resolve("test.yaml").also { it.toFile().writeText("name: hello") }
        validateYaml(yaml, schema)
    }

    @Test
    fun `invalid YAML throws`() {
        val tmpDir = Files.createTempDirectory("micraft-schema-test")
        val schemasDir = tmpDir.resolve("schemas").also { Files.createDirectories(it) }
        val schema =
            schemasDir.resolve("test.schema.json").also {
                it.toFile()
                    .writeText(
                        """{"$${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""")
            }
        val yaml = tmpDir.resolve("test.yaml").also { it.toFile().writeText("age: 42") }
        assertFailsWith<IllegalStateException> { validateYaml(yaml, schema) }
    }

    @Test
    fun `missing YAML is silently skipped`() {
        val tmpDir = Files.createTempDirectory("micraft-schema-test")
        val schemasDir = tmpDir.resolve("schemas").also { Files.createDirectories(it) }
        val schema =
            schemasDir.resolve("test.schema.json").also {
                it.toFile()
                    .writeText(
                        """{"$${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object"}""")
            }
        validateYaml(tmpDir.resolve("missing.yaml"), schema)
    }

    @Test
    fun `all project YAML configs pass their schemas`() {
        validateAllYamlConfigs(dataDir)
    }
}
