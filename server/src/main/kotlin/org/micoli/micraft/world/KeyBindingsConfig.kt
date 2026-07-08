package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("KeyBindingsConfig")

private val SECTION_SERIALIZER =
    MapSerializer(
        String.serializer(),
        MapSerializer(String.serializer(), ListSerializer(String.serializer())))

private val DEFAULT_RESOURCES_PATH = Path.of("resources/config/keybindings.yaml")

private fun loadDefaultSections(defaultsPath: Path): Map<String, Map<String, List<String>>> {
    val text = defaultsPath.readText()
    return Yaml.default.decodeFromString(SECTION_SERIALIZER, text)
}

fun defaultKeyBindings(defaultsPath: Path = DEFAULT_RESOURCES_PATH): Map<String, List<String>> =
    buildMap {
        for ((_, actions) in loadDefaultSections(defaultsPath)) putAll(actions)
    }

/**
 * Two levels of dynamic maps (category -> action -> keys), not a fixed data class, so this builds
 * the [YamlSection] tree by hand instead of via [yamlConfigSection]/[yamlMapSection].
 */
private fun keyBindingsSection(
    defaults: Map<String, Map<String, List<String>>>,
    node: YamlNode?,
): YamlSection =
    YamlSection(
        key = "",
        subsections =
            defaults.map { (section, actions) ->
                YamlSection(
                    key = section,
                    present = presentInYaml(node, section),
                    fields =
                        actions.map { (action, keys) ->
                            YamlField(
                                action,
                                keys,
                                ListSerializer(String.serializer()),
                                presentInYaml(node, section, action))
                        },
                )
            },
    )

fun loadKeyBindings(
    path: Path,
    defaultsPath: Path = DEFAULT_RESOURCES_PATH
): Map<String, List<String>> {
    val defaultSections = loadDefaultSections(defaultsPath)
    val originalText = if (path.exists()) path.readText() else ""
    val sections =
        if (path.exists()) {
            validateYamlConfig(path, "keybindings.schema.json")
            runCatching { Yaml.default.decodeFromString(SECTION_SERIALIZER, originalText) }
                .getOrElse { e ->
                    log.warn("Failed to parse keybindings.yaml ({}), using defaults", e.message)
                    defaultSections
                }
        } else {
            log.info("No keybindings.yaml at {}, creating with defaults", path.toAbsolutePath())
            defaultSections
        }
    path.parent?.createDirectories()
    if (originalText.isBlank()) {
        path.writeText(spliceMissingAsComments("", keyBindingsSection(defaultSections, null)))
    } else {
        runCatching { Yaml.default.parseToYamlNode(originalText) }
            .onSuccess { node ->
                path.writeText(
                    spliceMissingAsComments(
                        originalText, keyBindingsSection(defaultSections, node)))
            }
            .onFailure {
                log.warn(
                    "keybindings.yaml has unparseable structure, leaving file untouched: {}",
                    it.message)
            }
    }
    return buildMap { for ((_, actions) in sections) putAll(actions) }
}
