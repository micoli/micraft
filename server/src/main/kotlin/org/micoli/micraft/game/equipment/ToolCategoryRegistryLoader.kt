package org.micoli.micraft.game.equipment

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.micoli.micraft.config.isYamlEffectivelyEmpty
import org.micoli.micraft.config.mergeMapConfig
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.config.yamlMapSection
import org.micoli.micraft.game.world.EquipmentCategory
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ToolCategoryRegistryLoader")

private val ENTRY_MAP_SERIALIZER =
    MapSerializer(String.serializer(), ToolCategoryYamlEntry.serializer())

class ToolCategoryRegistryLoader(
    private val path: Path,
    private val resourcesPath: Path = Path.of("resources/config/tools.yaml"),
) {
    private val default: Map<String, ToolCategoryYamlEntry> =
        Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, resourcesPath.readText())

    init {
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(spliceMissingAsComments("", yamlMapSection(default, null)))
            log.info("Generated default tool category registry at {}", path.toAbsolutePath())
        } else {
            runCatching { Yaml.default.parseToYamlNode(originalText) }
                .onSuccess { node ->
                    path.writeText(
                        spliceMissingAsComments(
                            originalText, yamlMapSection(mergedEntries(node), node)))
                }
                .onFailure {
                    if (!originalText.isYamlEffectivelyEmpty())
                        log.warn(
                            "tools.yaml has unparseable structure, leaving file untouched: {}",
                            it.message)
                }
        }
        validateYamlConfig(path, "tools.schema.json")
    }

    private fun mergedEntries(node: YamlNode?): Map<String, ToolCategoryYamlEntry> {
        val originalText = if (path.exists()) path.readText() else ""
        val decoded =
            if (originalText.isBlank()) emptyMap()
            else
                runCatching { Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, originalText) }
                    .getOrElse { emptyMap() }
        return mergeMapConfig(decoded, default, node)
    }

    fun load(): Map<EquipmentCategory, ToolCategoryDefinition> {
        val originalText = if (path.exists()) path.readText() else ""
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        val raw = mergedEntries(node)
        val result =
            raw.mapNotNull { (key, entry) ->
                    runCatching { EquipmentCategory.valueOf(key) }
                        .getOrNull()
                        ?.let { it to ToolCategoryDefinition(mainHandOnly = entry.mainHandOnly) }
                }
                .toMap()
        log.info("Tool category registry loaded: {} categories", result.size)
        return result
    }
}
