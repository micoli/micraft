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
    val label: String = "",
    val bg: String = "",
)

private val ENTRY_MAP_SERIALIZER = MapSerializer(String.serializer(), ItemYamlEntry.serializer())

private val DEFAULT_YAML: Map<String, ItemYamlEntry> =
    mapOf(
        "COBBLESTONE" to
            ItemYamlEntry(buildable = true, placesBlock = "STONE", label = "COB", bg = "#7A7A7A"),
        "DIRT" to
            ItemYamlEntry(buildable = true, placesBlock = "DIRT", label = "DRT", bg = "#8B5A2B"),
        "SAND" to
            ItemYamlEntry(buildable = true, placesBlock = "SAND", label = "SND", bg = "#D5C89A"),
        "GRAVEL" to
            ItemYamlEntry(buildable = true, placesBlock = "GRAVEL", label = "GRV", bg = "#9A9A9A"),
        "SANDSTONE" to
            ItemYamlEntry(
                buildable = true, placesBlock = "SANDSTONE", label = "SST", bg = "#C8B46C"),
        "SNOWBALL" to ItemYamlEntry(buildable = false, label = "SNW", bg = "#DCE8F5"),
        "FLINT" to ItemYamlEntry(buildable = false, label = "FLT", bg = "#4A4A52"),
        "SEED" to
            ItemYamlEntry(buildable = true, placesBlock = "SEED", label = "SED", bg = "#C8A050"),
        "GRASS" to
            ItemYamlEntry(buildable = true, placesBlock = "GRASS", label = "GRS", bg = "#4A7A28"),
        "SNOW_BLOCK" to
            ItemYamlEntry(buildable = true, placesBlock = "SNOW", label = "SNB", bg = "#F0F0F0"),
        "OAK_LOG" to
            ItemYamlEntry(buildable = true, placesBlock = "OAK_LOG", label = "OLG", bg = "#654321"),
        "OAK_LEAVES" to
            ItemYamlEntry(
                buildable = true, placesBlock = "OAK_LEAVES", label = "OLV", bg = "#3C641E"),
        "PINE_LOG" to
            ItemYamlEntry(
                buildable = true, placesBlock = "PINE_LOG", label = "PLG", bg = "#503219"),
        "PINE_LEAVES" to
            ItemYamlEntry(
                buildable = true, placesBlock = "PINE_LEAVES", label = "PLV", bg = "#285A3C"),
        "PINE_LEAVES_SNOW" to
            ItemYamlEntry(
                buildable = true, placesBlock = "PINE_LEAVES_SNOW", label = "PLS", bg = "#C8D7DC"),
        "FLOWER" to
            ItemYamlEntry(buildable = true, placesBlock = "FLOWER", label = "FLW", bg = "#E6C832"),
        "WEED" to
            ItemYamlEntry(buildable = true, placesBlock = "WEED", label = "WED", bg = "#468228"),
    )

class ItemRegistryLoader(private val path: Path) {
    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(Yaml.default.encodeToString(ENTRY_MAP_SERIALIZER, DEFAULT_YAML))
            log.info("Generated default item registry at {}", path.toAbsolutePath())
        }
        validateYamlConfig(path, "items.schema.json")
    }

    fun load(): Map<ItemType, ItemDefinition> {
        val raw =
            runCatching { Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, path.readText()) }
                .getOrElse { e ->
                    log.warn("Failed to load items.yaml ({}), using defaults", e.message)
                    DEFAULT_YAML
                }
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
