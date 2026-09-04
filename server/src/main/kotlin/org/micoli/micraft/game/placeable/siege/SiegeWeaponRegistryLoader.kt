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
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.placeable.siege.SiegeWeaponDefinition
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SiegeWeaponRegistryLoader::class.java)

private val ENTRY_PROPERTIES =
    SiegeWeaponYamlEntry::class
        .memberProperties
        .associateBy { it.name }
        .mapValues { it.value.apply { isAccessible = true } }
private val OVERRIDE_PROPERTIES =
    SiegeWeaponYamlOverride::class
        .memberProperties
        .associateBy { it.name }
        .mapValues { it.value.apply { isAccessible = true } }

private val ENTRY_CONSTRUCTOR =
    SiegeWeaponYamlEntry::class.primaryConstructor!!.apply { isAccessible = true }

private fun SiegeWeaponYamlEntry.applyOverride(o: SiegeWeaponYamlOverride): SiegeWeaponYamlEntry {
    val args =
        ENTRY_CONSTRUCTOR.parameters.associateWith { param ->
            OVERRIDE_PROPERTIES.getValue(param.name!!).get(o)
                ?: ENTRY_PROPERTIES.getValue(param.name!!).get(this)
        }
    return ENTRY_CONSTRUCTOR.callBy(args)
}

/**
 * Directory-scan loader for siege weapon types — one `<name>/<name>.yaml` per weapon under
 * [resourcesWeaponsPath], optionally overridden by `<name>/<name>.yaml` under [dataWeaponsPath].
 * Mirrors [org.micoli.micraft.game.world.block.BlockRegistryLoader]'s shape exactly.
 */
class SiegeWeaponRegistryLoader(
    private val resourcesWeaponsPath: Path,
    private val dataWeaponsPath: Path,
) {
    private fun generateFromResources(): Map<String, SiegeWeaponYamlEntry> {
        val map = mutableMapOf<String, SiegeWeaponYamlEntry>()
        resourcesWeaponsPath
            .listDirectoryEntries()
            .filter { it.isDirectory() }
            .forEach { weaponDir ->
                val name = weaponDir.fileName.toString()
                val resourceYaml = weaponDir.resolve("$name.yaml")
                if (!resourceYaml.exists()) {
                    log.warn("No {}.yaml in {} — skipped", name, weaponDir)
                    return@forEach
                }
                validateYamlConfig(resourceYaml, "siege_weapons.schema.json")
                runCatching {
                        Yaml.default.decodeFromString(
                            SiegeWeaponYamlEntry.serializer(), resourceYaml.readText())
                    }
                    .onFailure { log.warn("Failed to load siege weapon {}: {}", name, it.message) }
                    .getOrNull()
                    ?.let { entry ->
                        val dataYaml = dataWeaponsPath.resolve("$name/$name.yaml")
                        val merged =
                            if (dataYaml.exists()) {
                                val content = dataYaml.readText()
                                val overrideResult =
                                    if (content.isNotBlank()) {
                                        validateYamlConfig(dataYaml, "siege_weapons.schema.json")
                                        runCatching {
                                            Yaml.default.decodeFromString(
                                                SiegeWeaponYamlOverride.serializer(), content)
                                        }
                                    } else Result.success(SiegeWeaponYamlOverride())
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
        log.info("Generated {} siege weapon definitions from resources", map.size)
        return map
    }

    fun load(): Map<EntityType, SiegeWeaponDefinition> {
        val result =
            generateFromResources().entries.associate { (key, entry) ->
                EntityType(key) to
                    SiegeWeaponDefinition(
                        bbmodelFile = entry.bbmodelFile,
                        width = entry.width,
                        height = entry.height,
                        projectileType = entry.projectileType,
                        ammoItem = entry.ammoItem.takeIf { it.isNotBlank() }?.let { ItemType(it) },
                        muzzleOffset = entry.muzzleOffset,
                        launchPower = entry.launchPower,
                        launchPowerMin = entry.launchPowerMin,
                        launchPowerMax = entry.launchPowerMax,
                        launchPitchDeg = entry.launchPitchDeg,
                        launchPitchDegMin = entry.launchPitchDegMin,
                        launchPitchDegMax = entry.launchPitchDegMax,
                        impactRadius = entry.impactRadius,
                        impactDamage = entry.impactDamage,
                        cooldownMs = entry.cooldownMs,
                        pitchStepRange = entry.pitchStepRange,
                        powerStepRange = entry.powerStepRange,
                    )
            }
        log.info("Siege weapon registry loaded: {} types", result.size)
        return result
    }

    fun reload(): Map<EntityType, SiegeWeaponDefinition> = load()
}
