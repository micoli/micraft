package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("BlockRegistryLoader")

@Serializable
private data class BlockYamlEntry(
    val hardness: Float = 1f,
    val solid: Boolean = true,
    val transparent: Boolean = false,
    val minimapColor: List<Int> = listOf(128, 128, 128),
    val modelElement: String = "",
    val liquid: Boolean = false,
    val viscosity: Int = 0,
    val replaceable: Boolean = false,
    val vegetationHost: Boolean = false,
    val treeAllowed: Boolean = true,
)

@Serializable
private data class BlockYamlOverride(
    val hardness: Float? = null,
    val solid: Boolean? = null,
    val transparent: Boolean? = null,
    val minimapColor: List<Int>? = null,
    val modelElement: String? = null,
    val liquid: Boolean? = null,
    val viscosity: Int? = null,
    val replaceable: Boolean? = null,
    val vegetationHost: Boolean? = null,
    val treeAllowed: Boolean? = null,
)

private fun BlockYamlEntry.applyOverride(o: BlockYamlOverride) =
    copy(
        hardness = o.hardness ?: hardness,
        solid = o.solid ?: solid,
        transparent = o.transparent ?: transparent,
        minimapColor = o.minimapColor ?: minimapColor,
        modelElement = o.modelElement ?: modelElement,
        liquid = o.liquid ?: liquid,
        viscosity = o.viscosity ?: viscosity,
        replaceable = o.replaceable ?: replaceable,
        vegetationHost = o.vegetationHost ?: vegetationHost,
        treeAllowed = o.treeAllowed ?: treeAllowed,
    )

private val ENTRY_MAP_SERIALIZER = MapSerializer(String.serializer(), BlockYamlEntry.serializer())

class BlockRegistryLoader(
    private val resourcesBlocksPath: Path,
    private val dataBlocksPath: Path,
    private val outputPath: Path,
) {
    init {
        generateFromResources()
    }

    private fun generateFromResources() {
        val map = mutableMapOf<String, BlockYamlEntry>()
        resourcesBlocksPath
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .forEach { blockDir ->
                val name = blockDir.fileName.toString()
                val resourceYaml = blockDir.resolve("$name.yaml")
                if (!resourceYaml.exists()) {
                    log.warn("No {}.yaml in {} — skipped", name, blockDir)
                    return@forEach
                }
                runCatching {
                        Yaml.default.decodeFromString(
                            BlockYamlEntry.serializer(), resourceYaml.readText())
                    }
                    .onFailure { log.warn("Failed to load block {}: {}", name, it.message) }
                    .getOrNull()
                    ?.let { entry ->
                        val dataYaml = dataBlocksPath.resolve("$name/$name.yaml")
                        val merged =
                            if (dataYaml.exists()) {
                                val content = dataYaml.readText()
                                val overridden =
                                    if (content.isNotBlank()) {
                                        runCatching {
                                                val override =
                                                    Yaml.default.decodeFromString(
                                                        BlockYamlOverride.serializer(), content)
                                                entry.applyOverride(override)
                                            }
                                            .onFailure {
                                                log.warn(
                                                    "Failed to apply override for {}: {}",
                                                    name,
                                                    it.message)
                                            }
                                            .getOrDefault(entry)
                                    } else entry
                                dataYaml.writeText(
                                    Yaml.default.encodeToString(
                                        BlockYamlEntry.serializer(), overridden))
                                log.debug("Wrote back merged data override for {}", name)
                                overridden
                            } else entry
                        map[name] = merged
                    }
            }
        outputPath.parent.createDirectories()
        outputPath.writeText(Yaml.default.encodeToString(ENTRY_MAP_SERIALIZER, map))
        log.info("Generated blocks.yaml from {} block definitions", map.size)
    }

    fun load(): Map<BlockType, BlockDefinition> {
        val raw =
            runCatching {
                    Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, outputPath.readText())
                }
                .getOrElse { e ->
                    log.warn("Failed to load blocks.yaml ({}), registry will be empty", e.message)
                    emptyMap()
                }
        val result =
            raw.entries
                .mapNotNull { (key, entry) ->
                    runCatching {
                            BlockType(key) to
                                BlockDefinition(
                                    hardness = entry.hardness,
                                    solid = entry.solid,
                                    transparent = entry.transparent,
                                    minimapColor = entry.minimapColor,
                                    modelElement = entry.modelElement,
                                    liquid = entry.liquid,
                                    viscosity = entry.viscosity,
                                    replaceable = entry.replaceable,
                                    vegetationHost = entry.vegetationHost,
                                    treeAllowed = entry.treeAllowed,
                                )
                        }
                        .onFailure {
                            log.warn("Unknown block type '{}' in blocks.yaml — skipped", key)
                        }
                        .getOrNull()
                }
                .toMap()
        log.info("Block registry loaded: {} block types", result.size)
        return result
    }

    fun reload(): Map<BlockType, BlockDefinition> {
        generateFromResources()
        return load()
    }
}
