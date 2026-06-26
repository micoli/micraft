package org.micoli.micraft.world

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("YamlSchemaValidator")
private val yamlMapper = ObjectMapper(YAMLFactory())
private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

fun validateYaml(yamlPath: Path, schemaPath: Path) {
    if (!yamlPath.exists()) {
        log.debug("YAML {} not found — skipping", yamlPath)
        return
    }
    val jsonNode = yamlMapper.readTree(yamlPath.toFile())
    val schema = schemaFactory.getSchema(schemaPath.toUri())
    val errors = schema.validate(jsonNode)
    if (errors.isNotEmpty()) {
        val detail = errors.joinToString("\n  - ", prefix = "\n  - ") { it.message }
        error("Schema validation failed for $yamlPath:$detail")
    }
    log.debug("OK {}", yamlPath)
}

fun validateAllYamlConfigs(dataDir: Path = Path.of("data")) {
    val schemasDir = dataDir.resolve("schemas")
    val staticPairs =
        listOf(
            dataDir.resolve("blocks/blocks.yaml") to schemasDir.resolve("blocks.schema.json"),
            dataDir.resolve("items/items.yaml") to schemasDir.resolve("items.schema.json"),
            dataDir.resolve("drops/drops.yaml") to schemasDir.resolve("drops.schema.json"),
            dataDir.resolve("biomes/biomes.yaml") to schemasDir.resolve("biomes.schema.json"),
            dataDir.resolve("server.yaml") to schemasDir.resolve("server.schema.json"),
            dataDir.resolve("auth/users.yaml") to schemasDir.resolve("auth-users.schema.json"),
            dataDir.resolve("personal/keybindings.yaml") to
                schemasDir.resolve("keybindings.schema.json"),
            dataDir.resolve("roads/roads.yaml") to schemasDir.resolve("roads.schema.json"),
            dataDir.resolve("houses/houses.yaml") to schemasDir.resolve("houses.schema.json"),
            dataDir.resolve("weather/weather.yaml") to schemasDir.resolve("weather.schema.json"),
        )

    val i18nSchema = schemasDir.resolve("i18n.schema.json")
    val i18nPairs =
        dataDir
            .resolve("i18n")
            .takeIf { it.exists() }
            ?.listDirectoryEntries("*.yaml")
            ?.map { it to i18nSchema } ?: emptyList()

    var ok = 0
    (staticPairs + i18nPairs).forEach { (yaml, schema) ->
        if (!schema.exists()) {
            log.debug("No schema for {} — skipping", yaml)
            return@forEach
        }
        validateYaml(yaml, schema)
        ok++
    }
    log.info("YAML config validation: {} file(s) validated", ok)
}
