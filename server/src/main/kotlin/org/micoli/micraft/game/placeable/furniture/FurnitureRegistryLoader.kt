package org.micoli.micraft.game.placeable.furniture

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
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.placeable.furniture.FurnitureDefinition
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(FurnitureRegistryLoader::class.java)

private val ENTRY_PROPERTIES =
    FurnitureYamlEntry::class
        .memberProperties
        .associateBy { it.name }
        .mapValues { it.value.apply { isAccessible = true } }
private val OVERRIDE_PROPERTIES =
    FurnitureYamlOverride::class
        .memberProperties
        .associateBy { it.name }
        .mapValues { it.value.apply { isAccessible = true } }

private val ENTRY_CONSTRUCTOR =
    FurnitureYamlEntry::class.primaryConstructor!!.apply { isAccessible = true }

private fun FurnitureYamlEntry.applyOverride(o: FurnitureYamlOverride): FurnitureYamlEntry {
    val args =
        ENTRY_CONSTRUCTOR.parameters.associateWith { param ->
            OVERRIDE_PROPERTIES.getValue(param.name!!).get(o)
                ?: ENTRY_PROPERTIES.getValue(param.name!!).get(this)
        }
    return ENTRY_CONSTRUCTOR.callBy(args)
}

/**
 * Directory-scan loader for furniture types — one `<name>/<name>.yaml` per furniture under
 * [resourcesFurnituresPath], optionally overridden by `<name>/<name>.yaml` under
 * [dataFurnituresPath]. Mirrors
 * [org.micoli.micraft.game.placeable.siege.SiegeWeaponRegistryLoader]'s shape exactly.
 */
class FurnitureRegistryLoader(
    private val resourcesFurnituresPath: Path =
        org.micoli.micraft.config.ConfigPaths.resourcesDir("furnitures"),
    private val dataFurnituresPath: Path =
        org.micoli.micraft.config.ConfigPaths.dataResources("furnitures"),
) {
    private fun generateFromResources(): Map<String, FurnitureYamlEntry> {
        if (!resourcesFurnituresPath.exists()) {
            log.info("No furniture resources dir at {}", resourcesFurnituresPath)
            return emptyMap()
        }
        val map = mutableMapOf<String, FurnitureYamlEntry>()
        resourcesFurnituresPath
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .forEach { furnitureDir ->
                val name = furnitureDir.fileName.toString()
                val resourceYaml = furnitureDir.resolve("$name.yaml")
                if (!resourceYaml.exists()) {
                    log.warn("No {}.yaml in {} — skipped", name, furnitureDir)
                    return@forEach
                }
                validateYamlConfig(resourceYaml, "furniture.schema.json")
                runCatching {
                        Yaml.default.decodeFromString(
                            FurnitureYamlEntry.serializer(), resourceYaml.readText())
                    }
                    .onFailure { log.warn("Failed to load furniture {}: {}", name, it.message) }
                    .getOrNull()
                    ?.let { entry ->
                        val dataYaml = dataFurnituresPath.resolve("$name/$name.yaml")
                        val merged =
                            if (dataYaml.exists()) {
                                val content = dataYaml.readText()
                                val overrideResult =
                                    if (content.isNotBlank()) {
                                        validateYamlConfig(dataYaml, "furniture.schema.json")
                                        runCatching {
                                            Yaml.default.decodeFromString(
                                                FurnitureYamlOverride.serializer(), content)
                                        }
                                    } else Result.success(FurnitureYamlOverride())
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
        log.info("Generated {} furniture definitions from resources", map.size)
        return map
    }

    fun load(): Map<EntityType, FurnitureDefinition> {
        val result =
            generateFromResources().entries.associate { (key, entry) ->
                EntityType(key) to
                    FurnitureDefinition(
                        bbmodelFile = entry.bbmodelFile,
                        width = entry.width,
                        height = entry.height,
                        rotatable = entry.rotatable,
                    )
            }
        log.info("Furniture registry loaded: {} types", result.size)
        return result
    }

    fun reload(): Map<EntityType, FurnitureDefinition> = load()
}
