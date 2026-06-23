package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val log = LoggerFactory.getLogger("BlockRegistryLoader")

@Serializable
private data class BlockYamlEntry(
    val hardness: Int = 1,
    val solid: Boolean = true,
    val transparent: Boolean = false,
    val minimapColor: List<Int> = listOf(128, 128, 128),
    val modelElement: String = "",
)

private val ENTRY_MAP_SERIALIZER = MapSerializer(String.serializer(), BlockYamlEntry.serializer())

private val DEFAULT_YAML: Map<String, BlockYamlEntry> = mapOf(
    "AIR"              to BlockYamlEntry(hardness = 0,  solid = false, transparent = true,  minimapColor = listOf(10,  10,  30)),
    "BEDROCK"          to BlockYamlEntry(hardness = -1, solid = true,  minimapColor = listOf(58,  58,  58)),
    "STONE"            to BlockYamlEntry(hardness = 5,  solid = true,  minimapColor = listOf(136, 136, 136)),
    "DIRT"             to BlockYamlEntry(hardness = 3,  solid = true,  minimapColor = listOf(122, 92,  46)),
    "GRASS"            to BlockYamlEntry(hardness = 3,  solid = true,  minimapColor = listOf(74,  122, 40)),
    "SAND"             to BlockYamlEntry(hardness = 2,  solid = true,  minimapColor = listOf(212, 200, 122)),
    "SANDSTONE"        to BlockYamlEntry(hardness = 4,  solid = true,  minimapColor = listOf(200, 160, 87)),
    "GRAVEL"           to BlockYamlEntry(hardness = 3,  solid = true,  minimapColor = listOf(128, 128, 128)),
    "SNOW"             to BlockYamlEntry(hardness = 1,  solid = true,  minimapColor = listOf(240, 240, 240)),
    "OAK_LOG"          to BlockYamlEntry(hardness = 3,  solid = true,  minimapColor = listOf(101, 67,  33)),
    "OAK_LEAVES"       to BlockYamlEntry(hardness = 1,  solid = true,  transparent = true, minimapColor = listOf(60,  100, 30)),
    "PINE_LOG"         to BlockYamlEntry(hardness = 3,  solid = true,  minimapColor = listOf(80,  50,  25)),
    "PINE_LEAVES"      to BlockYamlEntry(hardness = 1,  solid = true,  transparent = true, minimapColor = listOf(40,  90,  60)),
    "PINE_LEAVES_SNOW" to BlockYamlEntry(hardness = 1,  solid = true,  transparent = true, minimapColor = listOf(200, 215, 220)),
    "FLOWER"           to BlockYamlEntry(hardness = 1,  solid = false, transparent = true, minimapColor = listOf(230, 200, 50)),
    "WEED"             to BlockYamlEntry(hardness = 1,  solid = false, transparent = true, minimapColor = listOf(70,  130, 40)),
)

class BlockRegistryLoader(private val path: Path) {
    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(Yaml.default.encodeToString(ENTRY_MAP_SERIALIZER, DEFAULT_YAML))
            log.info("Generated default block registry at {}", path.toAbsolutePath())
        }
    }

    fun load(): Map<BlockType, BlockDefinition> {
        val raw = runCatching {
            Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, path.readText())
        }.getOrElse { e ->
            log.warn("Failed to load blocks.yaml ({}), using defaults", e.message)
            DEFAULT_YAML
        }
        val result = raw.entries.mapNotNull { (key, entry) ->
            runCatching {
                BlockType.valueOf(key) to BlockDefinition(
                    hardness = entry.hardness,
                    solid = entry.solid,
                    transparent = entry.transparent,
                    minimapColor = entry.minimapColor,
                    modelElement = entry.modelElement,
                )
            }.onFailure { log.warn("Unknown block type '{}' in blocks.yaml — skipped", key) }.getOrNull()
        }.toMap()
        log.info("Block registry loaded: {} block types", result.size)
        return result
    }

    fun reload(): Map<BlockType, BlockDefinition> = load()
}
