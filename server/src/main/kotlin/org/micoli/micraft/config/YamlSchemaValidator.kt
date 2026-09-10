package org.micoli.micraft.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import org.micoli.micraft.schema.JsonSchemaRoot
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("YamlSchemaValidator")
private val yamlMapper = ObjectMapper(YAMLFactory())
private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

/**
 * The one way to validate a game-data YAML file against its JSON Schema.
 *
 * Behaviour is uniform regardless of the call site:
 * - a missing or empty YAML file is skipped silently (`debug`);
 * - a missing schema resource is a `warn` (misconfiguration, but not fatal);
 * - schema violations are `warn` by default, or fatal when `MICRAFT_CONFIG_STRICT` is set — in
 *   strict mode [validate] throws and the caller (server boot) fails fast.
 *
 * The old `validateYaml` swallowed its own "validation failed" error inside the same `runCatching`
 * that guarded schema loading, so real violations were logged as "skipped". Here the two concerns
 * are separated.
 */
object SchemaValidation {
    val strict: Boolean =
        System.getenv("MICRAFT_CONFIG_STRICT")?.lowercase() in setOf("1", "true", "yes", "on")

    private fun schemaUri(name: String): URI? =
        SchemaValidation::class.java.getResource("/schemas/$name")?.toURI()

    /** Validates [yaml] against `/schemas/<schemaName>`. Throws in strict mode on violations. */
    fun validate(yaml: Path, schemaName: String) {
        val errors = errors(yaml, schemaName) ?: return
        if (errors.isEmpty()) {
            log.debug("Schema OK: {}", yaml)
            return
        }
        val detail = errors.joinToString(prefix = "\n  - ", separator = "\n  - ")
        if (strict) error("Schema validation failed for $yaml:$detail")
        log.warn("Schema validation errors in {} (non-strict, continuing):{}", yaml, detail)
    }

    /** Validates [yaml] against the schema named by [T]'s `@JsonSchemaRoot`. */
    inline fun <reified T : Any> validate(yaml: Path) = validate(yaml, schemaFileOf(T::class))

    /**
     * Returns the list of violation messages (empty = valid), or `null` when validation was skipped
     * (file absent/empty, or no schema resource). Never throws.
     */
    fun errors(yaml: Path, schemaName: String): List<String>? {
        if (!yaml.exists()) return null
        val node =
            runCatching { yamlMapper.readTree(yaml.toFile()) }
                .getOrElse {
                    log.warn("Cannot parse {} for schema validation: {}", yaml, it.message)
                    return null
                }
        if (node == null || node.isNull || node.isMissingNode) return null
        val uri =
            schemaUri(schemaName)
                ?: run {
                    log.warn("No schema resource /schemas/{} — skipping {}", schemaName, yaml)
                    return null
                }
        return runCatching { schemaFactory.getSchema(uri).validate(node).map { it.message } }
            .getOrElse {
                log.warn("Schema engine error for {}: {}", yaml, it.message)
                null
            }
    }
}

/** The schema file name declared by [kClass]'s `@JsonSchemaRoot` annotation. */
fun schemaFileOf(kClass: KClass<*>): String =
    kClass.findAnnotation<JsonSchemaRoot>()?.file
        ?: error("${kClass.simpleName} is not annotated with @JsonSchemaRoot")

// --- Thin façades kept for existing call sites -------------------------------------------------

fun validateYaml(yamlPath: Path, schemaUri: URI) {
    if (!yamlPath.exists()) return
    runCatching {
            val node = yamlMapper.readTree(yamlPath.toFile()) ?: return
            if (node.isNull || node.isMissingNode) return
            val errors = schemaFactory.getSchema(schemaUri).validate(node)
            if (errors.isNotEmpty()) {
                val detail = errors.joinToString("\n  - ", prefix = "\n  - ") { it.message }
                if (SchemaValidation.strict) error("Schema validation failed for $yamlPath:$detail")
                log.warn("Schema validation errors in {}:{}", yamlPath, detail)
            }
        }
        .onFailure { e ->
            if (SchemaValidation.strict) throw e
            log.warn("Schema validation skipped for {}: {}", yamlPath, e.message)
        }
}

fun validateYamlErrors(yamlPath: Path, schemaUri: URI): List<String> {
    if (!yamlPath.exists()) return emptyList()
    return runCatching {
            val node = yamlMapper.readTree(yamlPath.toFile()) ?: return emptyList()
            if (node.isNull || node.isMissingNode) return emptyList()
            schemaFactory.getSchema(schemaUri).validate(node).map { "$yamlPath: ${it.message}" }
        }
        .getOrElse { e -> listOf("$yamlPath: ${e.message}") }
}

fun validateYamlConfig(yaml: Path, schemaName: String): Boolean {
    SchemaValidation.validate(yaml, schemaName)
    return false
}

fun validateAlli18nYamlConfigs(configDir: Path) {
    val i18nFiles =
        configDir.resolve("i18n").takeIf { it.exists() }?.listDirectoryEntries("*.yaml")
            ?: emptyList()
    i18nFiles.forEach { SchemaValidation.validate(it, "i18n.schema.json") }
    log.info("YAML config validation: {} i18n file(s) checked", i18nFiles.size)
}
