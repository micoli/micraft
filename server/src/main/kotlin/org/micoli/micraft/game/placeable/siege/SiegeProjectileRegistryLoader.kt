package org.micoli.micraft.game.placeable.siege

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
import org.micoli.micraft.placeable.siege.SiegeProjectileDefinition
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SiegeProjectileRegistryLoader")

private val ENTRY_PROPERTIES =
    SiegeProjectileYamlEntry::class
        .memberProperties
        .associateBy { it.name }
        .mapValues { it.value.apply { isAccessible = true } }
private val OVERRIDE_PROPERTIES =
    SiegeProjectileYamlOverride::class
        .memberProperties
        .associateBy { it.name }
        .mapValues { it.value.apply { isAccessible = true } }

private val ENTRY_CONSTRUCTOR =
    SiegeProjectileYamlEntry::class.primaryConstructor!!.apply { isAccessible = true }

private fun SiegeProjectileYamlEntry.applyOverride(
    o: SiegeProjectileYamlOverride
): SiegeProjectileYamlEntry {
    val args =
        ENTRY_CONSTRUCTOR.parameters.associateWith { param ->
            OVERRIDE_PROPERTIES.getValue(param.name!!).get(o)
                ?: ENTRY_PROPERTIES.getValue(param.name!!).get(this)
        }
    return ENTRY_CONSTRUCTOR.callBy(args)
}

/**
 * Directory-scan loader for siege projectile types — one `<name>/<name>.yaml` per projectile under
 * [resourcesProjectilesPath], optionally overridden by `<name>/<name>.yaml` under
 * [dataProjectilesPath]. Mirrors [org.micoli.micraft.game.world.block.BlockRegistryLoader]'s shape
 * exactly (same pattern as [SiegeWeaponRegistryLoader]).
 */
class SiegeProjectileRegistryLoader(
    private val resourcesProjectilesPath: Path,
    private val dataProjectilesPath: Path,
) {
    private fun generateFromResources(): Map<String, SiegeProjectileYamlEntry> {
        val map = mutableMapOf<String, SiegeProjectileYamlEntry>()
        resourcesProjectilesPath
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .forEach { projectileDir ->
                val name = projectileDir.fileName.toString()
                val resourceYaml = projectileDir.resolve("$name.yaml")
                if (!resourceYaml.exists()) {
                    log.warn("No {}.yaml in {} — skipped", name, projectileDir)
                    return@forEach
                }
                validateYamlConfig(resourceYaml, "siege_projectiles.schema.json")
                runCatching {
                        Yaml.default.decodeFromString(
                            SiegeProjectileYamlEntry.serializer(), resourceYaml.readText())
                    }
                    .onFailure {
                        log.warn("Failed to load siege projectile {}: {}", name, it.message)
                    }
                    .getOrNull()
                    ?.let { entry ->
                        val dataYaml = dataProjectilesPath.resolve("$name/$name.yaml")
                        val merged =
                            if (dataYaml.exists()) {
                                val content = dataYaml.readText()
                                val overrideResult =
                                    if (content.isNotBlank()) {
                                        validateYamlConfig(
                                            dataYaml, "siege_projectiles.schema.json")
                                        runCatching {
                                            Yaml.default.decodeFromString(
                                                SiegeProjectileYamlOverride.serializer(), content)
                                        }
                                    } else Result.success(SiegeProjectileYamlOverride())
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
        log.info("Generated {} siege projectile definitions from resources", map.size)
        return map
    }

    fun load(): Map<EntityType, SiegeProjectileDefinition> {
        val result =
            generateFromResources().entries.associate { (key, entry) ->
                EntityType(key) to
                    SiegeProjectileDefinition(
                        bbmodelFile = entry.bbmodelFile, radius = entry.radius)
            }
        log.info("Siege projectile registry loaded: {} types", result.size)
        return result
    }

    fun reload(): Map<EntityType, SiegeProjectileDefinition> = load()
}
