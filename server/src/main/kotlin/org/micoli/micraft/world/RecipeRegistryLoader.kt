package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
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

private val DEFAULT_YAML: Map<String, RecipeYamlEntry> =
    mapOf(
        "COBBLESTONE_BRICK" to
            RecipeYamlEntry(
                giveType = "block",
                giveId = "COBBLESTONE",
                giveAmount = 4,
                items = listOf("COBBLESTONE*2", "GRAVEL*1"),
            ),
        "DIRT_PILE" to
            RecipeYamlEntry(
                giveType = "item",
                giveId = "DIRT",
                giveAmount = 2,
                items = listOf("GRAVEL*2", "SAND*1"),
            ),
    )

class RecipeRegistryLoader(private val path: Path) {
    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(Yaml.default.encodeToString(ENTRY_MAP_SERIALIZER, DEFAULT_YAML))
            log.info("Generated default recipe registry at {}", path.toAbsolutePath())
        }
        validateYamlConfig(path, "recipes.schema.json")
    }

    fun load(): Map<String, RecipeDefinition> {
        val raw =
            runCatching { Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, path.readText()) }
                .getOrElse { e ->
                    log.warn("Failed to load recipes.yaml ({}), using defaults", e.message)
                    DEFAULT_YAML
                }
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
