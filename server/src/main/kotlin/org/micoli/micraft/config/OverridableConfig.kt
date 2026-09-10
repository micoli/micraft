package org.micoli.micraft.config

import com.charleskorn.kaml.Yaml
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory

@PublishedApi internal val overridableCfgLog = LoggerFactory.getLogger("OverridableConfig")

@PublishedApi
internal fun schemaHeader(schemaFile: String) =
    "# yaml-language-server: \$schema=../schemas/$schemaFile"

/**
 * The one way to load a single-file overridable config (`@JsonSchemaRoot(root = OBJECT)`): decode
 * the shipped default from `resources/config/<name>`, fuse in the user's `data/config/<name>`
 * (field-by-field via [mergeConfig]), validate it against its schema, and rewrite the user file
 * with any missing key spliced back as a `# key: value` comment.
 *
 * Replaces the body that used to be copy-pasted in `loadServerConfig`, `WeatherConfig.load`,
 * `CombatConfig`, `ClassesConfig`, `ExperienceConfig`, `VegetationConfig`, …
 */
inline fun <reified T : Any> loadOverridableConfig(
    name: String,
    paths: OverridablePaths = ConfigPaths.configPair(name),
    schemaFile: String? = null,
): T = loadOverridableConfig(name, paths, schemaFile, T::class, serializer<T>())

fun <T : Any> loadOverridableConfig(
    name: String,
    paths: OverridablePaths,
    schemaFileOrNull: String?,
    kClass: KClass<T>,
    serializer: KSerializer<T>,
): T {
    val schemaFile = schemaFileOrNull ?: schemaFileOf(kClass)
    val (resourcesPath, path) = paths

    val default = Yaml.default.decodeFromString(serializer, resourcesPath.readText())
    val originalText = if (path.exists()) path.readText() else ""
    path.parent?.createDirectories()

    if (originalText.isBlank()) {
        path.writeText(
            schemaHeader(schemaFile) +
                "\n" +
                spliceMissingAsComments("", yamlConfigSection(kClass, "", default, null)))
        overridableCfgLog.info("Generated default {} at {}", name, path.toAbsolutePath())
        return default
    }

    val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
    if (node == null) {
        if (!originalText.isYamlEffectivelyEmpty())
            overridableCfgLog.warn("{} has unparseable structure, leaving file untouched", name)
        return default
    }

    val decoded =
        runCatching { Yaml.default.decodeFromString(serializer, originalText) }
            .getOrElse { e ->
                overridableCfgLog.warn("Failed to parse {} ({}), using defaults", name, e.message)
                default
            }
    val merged = mergeConfig(kClass, decoded, default, node)
    path.writeText(
        spliceMissingAsComments(originalText, yamlConfigSection(kClass, "", merged, node)))
    SchemaValidation.validate(path, schemaFile)
    return merged
}

/**
 * Map-of-entries variant (`@JsonSchemaRoot(root = MAP_OF)`) — items, recipes, weapon/tool
 * categories, plain colors, block ids. Same merge-and-write-back contract as
 * [loadOverridableConfig], by key via [mergeMapConfig] / [yamlMapSection].
 */
inline fun <reified T : Any> loadOverridableMapConfig(
    name: String,
    paths: OverridablePaths = ConfigPaths.configPair(name),
): Map<String, T> {
    val schemaFile = schemaFileOf(T::class)
    val (resourcesPath, path) = paths
    val entrySerializer = MapSerializer(String.serializer(), serializer<T>())

    val default: Map<String, T> =
        Yaml.default.decodeFromString(entrySerializer, resourcesPath.readText())
    val originalText = if (path.exists()) path.readText() else ""
    path.parent?.createDirectories()

    if (originalText.isBlank()) {
        path.writeText(
            schemaHeader(schemaFile) +
                "\n" +
                spliceMissingAsComments("", yamlMapSection(default, null)))
        overridableCfgLog.info("Generated default {} at {}", name, path.toAbsolutePath())
        return default
    }

    val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
    if (node == null) {
        if (!originalText.isYamlEffectivelyEmpty())
            overridableCfgLog.warn("{} has unparseable structure, leaving file untouched", name)
        return default
    }
    val decoded =
        runCatching { Yaml.default.decodeFromString(entrySerializer, originalText) }
            .getOrElse { emptyMap() }
    val merged = mergeMapConfig(decoded, default, node)
    path.writeText(spliceMissingAsComments(originalText, yamlMapSection(merged, node)))
    SchemaValidation.validate(path, schemaFile)
    return merged
}

/**
 * Directory-based overridable registry — one `<name>/<name>.yaml` per entry under
 * `resources/<sub>`, each optionally overridden by the same path under `data/resources/<sub>`.
 * Extracts the per-file "decode entry, apply data override, validate, splice missing keys back as
 * comments" loop that was duplicated across the block / armor / weapon / tool / npc / siege /
 * furniture loaders.
 *
 * The caller supplies how an override [O] is folded onto an entry [E] and an empty [O]; the schema
 * comes from [E]'s `@JsonSchemaRoot`. Returns the merged entries keyed by directory name — the
 * caller maps each to its domain definition.
 */
inline fun <reified E : Any, reified O : Any> loadOverridableDir(
    paths: OverridablePaths,
    entrySerializer: KSerializer<E> = serializer(),
    overrideSerializer: KSerializer<O> = serializer(),
    crossinline emptyOverride: () -> O,
    crossinline applyOverride: (E, O) -> E,
): Map<String, E> {
    if (!paths.resources.exists()) return emptyMap()
    val schemaFile = schemaFileOf(E::class)
    val result =
        paths.resources
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .mapNotNull { dir ->
                val name = dir.fileName.toString()
                val entryYaml = dir.resolve("$name.yaml")
                if (!entryYaml.exists()) {
                    overridableCfgLog.warn("No {}.yaml in {} — skipped", name, dir)
                    return@mapNotNull null
                }
                SchemaValidation.validate(entryYaml, schemaFile)
                val entry =
                    runCatching {
                            Yaml.default.decodeFromString(entrySerializer, entryYaml.readText())
                        }
                        .onFailure {
                            overridableCfgLog.warn(
                                "Failed to load {} '{}': {}", schemaFile, name, it.message)
                        }
                        .getOrNull() ?: return@mapNotNull null

                val dataYaml = paths.data.resolve("$name/$name.yaml")
                if (!dataYaml.exists()) return@mapNotNull name to entry

                val content = dataYaml.readText()
                val overrideResult =
                    if (content.isNotBlank()) {
                        SchemaValidation.validate(dataYaml, schemaFile)
                        runCatching { Yaml.default.decodeFromString(overrideSerializer, content) }
                    } else Result.success(emptyOverride())
                val overridden =
                    overrideResult.getOrNull()?.let { applyOverride(entry, it) } ?: entry
                overrideResult
                    .mapCatching { Yaml.default.parseToYamlNode(content.ifBlank { "{}" }) }
                    .fold(
                        onSuccess = { node ->
                            dataYaml.writeText(
                                spliceMissingAsComments(
                                    content, yamlConfigSection(E::class, "", overridden, node)))
                        },
                        onFailure = {
                            overridableCfgLog.warn(
                                "Failed to apply override for {}, leaving file untouched: {}",
                                name,
                                it.message)
                        })
                name to overridden
            }
            .toMap()
    return result
}

/** [loadOverridableDir] shorthand for [org.micoli.micraft.config.ConfigPaths.dirPair]. */
inline fun <reified E : Any, reified O : Any> loadOverridableDir(
    sub: String,
    crossinline emptyOverride: () -> O,
    crossinline applyOverride: (E, O) -> E,
): Map<String, E> =
    loadOverridableDir(
        ConfigPaths.dirPair(sub),
        emptyOverride = emptyOverride,
        applyOverride = applyOverride,
    )
