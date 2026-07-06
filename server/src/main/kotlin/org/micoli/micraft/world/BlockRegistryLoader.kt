package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlinx.serialization.Serializable
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

private val ENTRY_PROPERTIES =
    BlockYamlEntry::class
        .memberProperties
        .associateBy { it.name }
        .mapValues { it.value.apply { isAccessible = true } }
private val OVERRIDE_PROPERTIES =
    BlockYamlOverride::class
        .memberProperties
        .associateBy { it.name }
        .mapValues { it.value.apply { isAccessible = true } }

private val ENTRY_CONSTRUCTOR =
    BlockYamlEntry::class.primaryConstructor!!.apply { isAccessible = true }

private fun BlockYamlEntry.applyOverride(o: BlockYamlOverride): BlockYamlEntry {
    val args =
        ENTRY_CONSTRUCTOR.parameters.associateWith { param ->
            OVERRIDE_PROPERTIES.getValue(param.name!!).get(o)
                ?: ENTRY_PROPERTIES.getValue(param.name!!).get(this)
        }
    return ENTRY_CONSTRUCTOR.callBy(args)
}

class BlockRegistryLoader(
    private val resourcesBlocksPath: Path,
    private val dataBlocksPath: Path,
) {
    private fun generateFromResources(): Map<String, BlockYamlEntry> {
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
                                val overrideResult =
                                    if (content.isNotBlank()) {
                                        runCatching {
                                            Yaml.default.decodeFromString(
                                                BlockYamlOverride.serializer(), content)
                                        }
                                    } else Result.success(BlockYamlOverride())
                                val override = overrideResult.getOrNull()
                                val overridden = override?.let { entry.applyOverride(it) } ?: entry
                                overrideResult.fold(
                                    onSuccess = {
                                        dataYaml.writeText(
                                            spliceMissingAsComments(
                                                content, yamlOverrideSection(overridden, it)))
                                        log.debug("Wrote back merged data override for {}", name)
                                    },
                                    onFailure = {
                                        log.warn(
                                            "Failed to apply override for {}, leaving file untouched: {}",
                                            name,
                                            it.message)
                                    })
                                overridden
                            } else entry
                        map[name] = merged
                    }
            }
        log.info("Generated {} block definitions from resources", map.size)
        return map
    }

    fun load(): Map<BlockType, BlockDefinition> {
        val result =
            generateFromResources()
                .entries
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
                            log.warn("Unknown block type '{}' in resources/blocks — skipped", key)
                        }
                        .getOrNull()
                }
                .toMap()
        log.info("Block registry loaded: {} block types", result.size)
        return result
    }

    fun reload(): Map<BlockType, BlockDefinition> = load()
}
