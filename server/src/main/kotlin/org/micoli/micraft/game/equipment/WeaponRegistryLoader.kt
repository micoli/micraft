package org.micoli.micraft.game.equipment

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.yamlConfigSection
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(WeaponRegistryLoader::class.java)

private fun WeaponYamlEntry.applyOverride(o: WeaponYamlOverride) =
    copy(
        category = o.category ?: category,
        statBonus = o.statBonus ?: statBonus,
        rotate = o.rotate ?: rotate)

class WeaponRegistryLoader(
    private val weaponsPath: Path,
    private val dataWeaponsPath: Path,
) {
    fun load(): Map<String, WeaponDefinition> {
        if (!weaponsPath.exists()) return emptyMap()
        val result =
            weaponsPath
                .listDirectoryEntries()
                .filter { it.isDirectory() }
                .mapNotNull { dir ->
                    val name = dir.fileName.toString()
                    val yaml = dir.resolve("$name.yaml")
                    if (!yaml.exists()) return@mapNotNull null
                    runCatching {
                            Yaml.default.decodeFromString(
                                WeaponYamlEntry.serializer(), yaml.readText())
                        }
                        .onFailure { log.warn("Failed to load weapon '{}': {}", name, it.message) }
                        .getOrNull()
                        ?.let { entry ->
                            val dataYaml = dataWeaponsPath.resolve("$name/$name.yaml")
                            val merged =
                                if (dataYaml.exists()) {
                                    val content = dataYaml.readText()
                                    val overrideResult =
                                        if (content.isNotBlank()) {
                                            runCatching {
                                                Yaml.default.decodeFromString(
                                                    WeaponYamlOverride.serializer(), content)
                                            }
                                        } else Result.success(WeaponYamlOverride())
                                    val override = overrideResult.getOrNull()
                                    val overridden =
                                        override?.let { entry.applyOverride(it) } ?: entry
                                    overrideResult
                                        .mapCatching {
                                            Yaml.default.parseToYamlNode(content.ifBlank { "{}" })
                                        }
                                        .fold(
                                            onSuccess = { node ->
                                                dataYaml.writeText(
                                                    spliceMissingAsComments(
                                                        content,
                                                        yamlConfigSection(
                                                            WeaponYamlEntry::class,
                                                            "",
                                                            overridden,
                                                            node)))
                                                log.debug(
                                                    "Wrote back merged data override for {}", name)
                                            },
                                            onFailure = {
                                                log.warn(
                                                    "Failed to apply override for {}, leaving file untouched: {}",
                                                    name,
                                                    it.message)
                                            })
                                    overridden
                                } else entry
                            name to
                                WeaponDefinition(
                                    category = merged.category,
                                    statBonus = merged.statBonus,
                                    rotate = merged.rotate)
                        }
                }
                .toMap()
        log.info("Weapon registry loaded: {} weapon types", result.size)
        return result
    }
}
