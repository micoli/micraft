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

private val log = LoggerFactory.getLogger("ItemRegistryLoader")

@Serializable
private data class ItemYamlEntry(
    val buildable: Boolean = false,
    val placesBlock: String? = null,
)

private val ENTRY_MAP_SERIALIZER = MapSerializer(String.serializer(), ItemYamlEntry.serializer())

private val DEFAULT_YAML: Map<String, ItemYamlEntry> =
    mapOf(
        "COBBLESTONE" to ItemYamlEntry(buildable = true, placesBlock = "STONE"),
        "DIRT" to ItemYamlEntry(buildable = true, placesBlock = "DIRT"),
        "SAND" to ItemYamlEntry(buildable = true, placesBlock = "SAND"),
        "GRAVEL" to ItemYamlEntry(buildable = true, placesBlock = "GRAVEL"),
        "SANDSTONE" to ItemYamlEntry(buildable = true, placesBlock = "SANDSTONE"),
        "SNOWBALL" to ItemYamlEntry(buildable = false),
        "FLINT" to ItemYamlEntry(buildable = false),
    )

class ItemRegistryLoader(private val path: Path) {
    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(Yaml.default.encodeToString(ENTRY_MAP_SERIALIZER, DEFAULT_YAML))
            log.info("Generated default item registry at {}", path.toAbsolutePath())
        }
    }

    fun load(): Map<ItemType, ItemDefinition> {
        val raw =
            runCatching { Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, path.readText()) }
                .getOrElse { e ->
                    log.warn("Failed to load items.yaml ({}), using defaults", e.message)
                    DEFAULT_YAML
                }
        val result =
            raw.entries
                .mapNotNull { (key, entry) ->
                    runCatching {
                            val placesBlock = entry.placesBlock?.let { BlockType(it) }
                            ItemType.valueOf(key) to
                                ItemDefinition(
                                    buildable = entry.buildable, placesBlock = placesBlock)
                        }
                        .onFailure {
                            log.warn("Unknown item type '{}' in items.yaml — skipped", key)
                        }
                        .getOrNull()
                }
                .toMap()
        log.info("Item registry loaded: {} item types", result.size)
        return result
    }

    fun reload(): Map<ItemType, ItemDefinition> = load()
}
