package org.micoli.micraft.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import org.slf4j.LoggerFactory

class YamlSchemaValidatorTest {

    private val dataDir: Path = Path.of(System.getProperty("projectDir", ".")).resolve("data")

    private fun tempSchema(content: String): URI {
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
    fun `invalid YAML is logged and does not throw`() {
        val schema =
            tempSchema(
                """{"$${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""")
        val yaml =
            Files.createTempFile("micraft-yaml-test", ".yaml").also {
                it.toFile().writeText("age: 42")
            }
        validateYaml(yaml, schema)
    }

    @Test
    fun `missing YAML is silently skipped`() {
        val schema =
            tempSchema(
                """{"$${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object"}""")
        validateYaml(Path.of("/tmp/micraft-nonexistent-${System.nanoTime()}.yaml"), schema)
    }

    @Test
    fun `empty YAML file is skipped without error`() {
        val logger = LoggerFactory.getLogger("YamlSchemaValidator") as Logger
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        logger.addAppender(appender)
        try {
            val schema =
                tempSchema(
                    """{"$${"$"}schema":"http://json-schema.org/draft-07/schema#","type":"object","required":["name"]}""")
            val yaml =
                Files.createTempFile("micraft-yaml-empty", ".yaml").also {
                    it.toFile().writeText("")
                }
            validateYaml(yaml, schema)
            val blankOnly =
                Files.createTempFile("micraft-yaml-blank", ".yaml").also {
                    it.toFile().writeText("# just a comment\n")
                }
            validateYaml(blankOnly, schema)
        } finally {
            logger.detachAppender(appender)
        }
        assert(appender.list.none { it.level == Level.WARN }) {
            "expected no WARN, got: ${appender.list.map { it.formattedMessage }}"
        }
    }

    @Test
    fun `all project YAML configs pass their schemas`() {
        validateAlli18nYamlConfigs(dataDir.resolve("config"))
    }
}
