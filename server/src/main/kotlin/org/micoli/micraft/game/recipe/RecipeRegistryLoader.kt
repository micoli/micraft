package org.micoli.micraft.game.recipe

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
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.RecipeDefinition
import org.micoli.micraft.game.world.RecipeIngredient
import org.slf4j.LoggerFactory

private val recipeLog = LoggerFactory.getLogger("RecipeRegistryLoader")

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
            recipeLog.info("Generated default recipe registry at {}", path.toAbsolutePath())
        } else {
            runCatching { Yaml.default.parseToYamlNode(originalText) }
                .onSuccess { node ->
                    path.writeText(
                        spliceMissingAsComments(
                            originalText, yamlMapSection(mergedEntries(node), node)))
                }
                .onFailure {
                    if (!originalText.isYamlEffectivelyEmpty()) recipeLog.warn(
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
        recipeLog.info("Recipe registry loaded: {} recipes", result.size)
        return result
    }

    fun reload(): Map<String, RecipeDefinition> = load()

    companion object {
        fun parseIngredient(entry: String): RecipeIngredient {
            val parts = entry.split("*")
            val type = ItemType(parts[0].trim().uppercase())
            val count = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            return RecipeIngredient(type = type, count = count)
        }
    }
}
