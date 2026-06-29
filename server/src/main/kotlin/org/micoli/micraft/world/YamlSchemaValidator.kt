package org.micoli.micraft.world

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("YamlSchemaValidator")
private val yamlMapper = ObjectMapper(YAMLFactory())
private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

fun validateYaml(yamlPath: Path, schemaUri: URI) {
    if (!yamlPath.exists()) {
        log.debug("YAML {} not found — skipping", yamlPath)
        return
    }
    val jsonNode = yamlMapper.readTree(yamlPath.toFile())
    val schema = schemaFactory.getSchema(schemaUri)
    val errors = schema.validate(jsonNode)
    if (errors.isNotEmpty()) {
        val detail = errors.joinToString("\n  - ", prefix = "\n  - ") { it.message }
        error("Schema validation failed for $yamlPath:$detail")
    }
    log.debug("OK {}", yamlPath)
}

private fun schemaUri(name: String): URI? =
    object {}::class.java.getResource("/schemas/$name")?.toURI()

fun validateAllYamlConfigs(configDir: Path = Path.of("data/config")) {
    val staticPairs =
        listOf(
            configDir.resolve("blocks.yaml") to "blocks.schema.json",
            configDir.resolve("items.yaml") to "items.schema.json",
            configDir.resolve("drops.yaml") to "drops.schema.json",
            configDir.resolve("biomes.yaml") to "biomes.schema.json",
            configDir.resolve("server.yaml") to "server.schema.json",
            configDir.resolve("game.yaml") to "game.schema.json",
            configDir.resolve("auth/users.yaml") to "auth-users.schema.json",
            configDir.resolve("keybindings.yaml") to "keybindings.schema.json",
            configDir.resolve("roads.yaml") to "roads.schema.json",
            configDir.resolve("houses.yaml") to "houses.schema.json",
            configDir.resolve("weather.yaml") to "weather.schema.json",
        )

    val i18nPairs =
        configDir
            .resolve("i18n")
            .takeIf { it.exists() }
            ?.listDirectoryEntries("*.yaml")
            ?.map { it to "i18n.schema.json" } ?: emptyList()

    var ok = 0
    (staticPairs + i18nPairs).forEach { (yaml, schemaName) ->
        val uri = schemaUri(schemaName) ?: run {
            log.debug("No schema resource for {} — skipping", yaml)
            return@forEach
        }
        validateYaml(yaml, uri)
        ok++
    }
    log.info("YAML config validation: {} file(s) validated", ok)
}
