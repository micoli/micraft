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

private val log = LoggerFactory.getLogger("RecipeRegistryLoader")

@Serializable
private data class RecipeYamlEntry(
    val giveType: String = "item",
    val giveId: String = "",
    val giveAmount: Int = 1,
    val items: List<String> = emptyList(),
)

private val ENTRY_MAP_SERIALIZER = MapSerializer(String.serializer(), RecipeYamlEntry.serializer())

class RecipeRegistryLoader(
    private val path: Path,
    private val resourcesPath: Path = Path.of("resources/config/recipes.yaml"),
) {
    private val default: Map<String, RecipeYamlEntry> =
        Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, resourcesPath.readText())

    init {
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(spliceMissingAsComments("", yamlMapSection(default, null)))
            log.info("Generated default recipe registry at {}", path.toAbsolutePath())
        } else {
            runCatching { Yaml.default.parseToYamlNode(originalText) }
                .onSuccess { node ->
                    path.writeText(
                        spliceMissingAsComments(
                            originalText, yamlMapSection(mergedEntries(node), node)))
                }
                .onFailure {
                    log.warn(
                        "recipes.yaml has unparseable structure, leaving file untouched: {}",
                        it.message)
                }
        }
        validateYamlConfig(path, "recipes.schema.json")
    }

    private fun mergedEntries(node: YamlNode?): Map<String, RecipeYamlEntry> {
        val originalText = if (path.exists()) path.readText() else ""
        val decoded =
            if (originalText.isBlank()) emptyMap()
            else
                runCatching { Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, originalText) }
                    .getOrElse { emptyMap() }
        return mergeMapConfig(decoded, default, node)
    }

    fun load(): Map<String, RecipeDefinition> {
        val originalText = if (path.exists()) path.readText() else ""
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        val raw = mergedEntries(node)
        val result =
            raw.entries.associate { (id, entry) ->
                id to
                    RecipeDefinition(
                        giveType = entry.giveType,
                        giveId = entry.giveId,
                        giveAmount = entry.giveAmount,
                        ingredients = entry.items.map { parseIngredient(it) },
                    )
            }
        log.info("Recipe registry loaded: {} recipes", result.size)
        return result
    }

    fun reload(): Map<String, RecipeDefinition> = load()
}
