package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ItemRegistryLoader")

@Serializable
private data class ItemYamlEntry(
    val buildable: Boolean = false,
    val placesBlock: String? = null,
    val label: String = "",
    val bg: String = "",
)

private val ENTRY_MAP_SERIALIZER = MapSerializer(String.serializer(), ItemYamlEntry.serializer())

class ItemRegistryLoader(
    private val path: Path,
    private val resourcesPath: Path = Path.of("resources/config/items.yaml"),
) {
    private val default: Map<String, ItemYamlEntry> =
        Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, resourcesPath.readText())

    init {
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(spliceMissingAsComments("", yamlMapSection(default, null)))
            log.info("Generated default item registry at {}", path.toAbsolutePath())
        } else {
            runCatching { Yaml.default.parseToYamlNode(originalText) }
                .onSuccess { node ->
                    path.writeText(
                        spliceMissingAsComments(
                            originalText, yamlMapSection(mergedEntries(node), node)))
                }
                .onFailure {
                    log.warn(
                        "items.yaml has unparseable structure, leaving file untouched: {}",
                        it.message)
                }
        }
        validateYamlConfig(path, "items.schema.json")
    }

    private fun mergedEntries(node: YamlNode?): Map<String, ItemYamlEntry> {
        val originalText = if (path.exists()) path.readText() else ""
        val decoded =
            if (originalText.isBlank()) emptyMap()
            else
                runCatching { Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, originalText) }
                    .getOrElse { emptyMap() }
        return mergeMapConfig(decoded, default, node)
    }

    fun load(): Map<ItemType, ItemDefinition> {
        val originalText = if (path.exists()) path.readText() else ""
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        val raw = mergedEntries(node)
        val result =
            raw.entries.associate { (key, entry) ->
                val placesBlock = entry.placesBlock?.let { BlockType(it) }
                ItemType(key) to
                    ItemDefinition(
                        buildable = entry.buildable,
                        placesBlock = placesBlock,
                        label = entry.label,
                        bg = entry.bg,
                    )
            }
        log.info("Item registry loaded: {} item types", result.size)
        return result
    }

    fun reload(): Map<ItemType, ItemDefinition> = load()
}
