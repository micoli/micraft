package org.micoli.micraft.game.world.block

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
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.config.yamlOverrideSection
import org.micoli.micraft.game.world.BlockDefinition
import org.micoli.micraft.game.world.BlockType
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("BlockRegistryLoader")

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
                validateYamlConfig(resourceYaml, "blocks.schema.json")
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
                                        validateYamlConfig(dataYaml, "blocks.schema.json")
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

    private fun toBlockTypes(merged: Map<String, BlockYamlEntry>): Map<BlockType, BlockYamlEntry> =
        merged.entries
            .mapNotNull { (key, entry) ->
                runCatching { BlockType(key) to entry }
                    .onFailure {
                        log.warn("Unknown block type '{}' in resources/blocks — skipped", key)
                    }
                    .getOrNull()
            }
            .toMap()

    fun load(): Map<BlockType, BlockDefinition> {
        val result =
            toBlockTypes(generateFromResources()).mapValues { (type, entry) ->
                val (topColor, sideColor) =
                    BlockFaceColorSampler.sample(
                        resourcesBlocksPath,
                        entry.modelElement.ifBlank { type.id },
                        entry.minimapColor,
                    )
                BlockDefinition(
                    hardness = entry.hardness,
                    solid = entry.solid,
                    transparent = entry.transparent,
                    minimapColor = entry.minimapColor,
                    topColor = topColor,
                    sideColor = sideColor,
                    modelElement = entry.modelElement,
                    gltfModel = entry.gltfModel,
                    liquid = entry.liquid,
                    viscosity = entry.viscosity,
                    replaceable = entry.replaceable,
                    vegetationHost = entry.vegetationHost,
                    treeAllowed = entry.treeAllowed,
                    minimapVisible = entry.minimapVisible,
                    rotatable = entry.rotatable,
                    hasStuds = entry.hasStuds,
                    brickSize = entry.brickSize,
                    heightFraction = entry.heightFraction,
                    plainColorable = entry.plainColorable,
                    isCubic = entry.isCubic,
                )
            }
        log.info("Block registry loaded: {} block types", result.size)
        return result
    }

    fun loadDropTable(): Map<BlockType, List<DropEntry>> =
        toBlockTypes(generateFromResources())
            .mapNotNull { (blockType, entry) ->
                entry.drops.takeIf { it.isNotEmpty() }?.let { blockType to it }
            }
            .toMap()

    fun reload(): Map<BlockType, BlockDefinition> = load()
}
