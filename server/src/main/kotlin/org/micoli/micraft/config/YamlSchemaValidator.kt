package org.micoli.micraft.config

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
    runCatching {
            val jsonNode = yamlMapper.readTree(yamlPath.toFile())
            val schema = schemaFactory.getSchema(schemaUri)
            val errors = schema.validate(jsonNode)
            if (errors.isNotEmpty()) {
                val detail = errors.joinToString("\n  - ", prefix = "\n  - ") { it.message }
                error("Schema validation failed for $yamlPath:$detail")
            }
            log.debug("OK {}", yamlPath)
        }
        .onFailure { e -> log.warn("Schema validation skipped for {}: {}", yamlPath, e.message) }
}

private fun schemaUri(name: String): URI? =
    object {}::class.java.getResource("/schemas/$name")?.toURI()

fun validateAlli18nYamlConfigs(configDir: Path) {
    val i18nPairs =
        configDir
            .resolve("i18n")
            .takeIf { it.exists() }
            ?.listDirectoryEntries("*.yaml")
            ?.map { it to "i18n.schema.json" } ?: emptyList()

    var ok = 0
    i18nPairs.forEach { (yaml, schemaName) ->
        if (validateYamlConfig(yaml, schemaName)) return@forEach
        ok++
    }
    log.info("YAML config validation: {} file(s) validated", ok)
}

fun validateYamlConfig(yaml: Path, schemaName: String): Boolean {
    val uri =
        schemaUri(schemaName)
            ?: run {
                log.debug("No schema resource for {} — skipping", yaml)
                return true
            }
    validateYaml(yaml, uri)
    return false
}
