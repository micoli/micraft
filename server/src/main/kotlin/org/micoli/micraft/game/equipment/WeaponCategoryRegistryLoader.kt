package org.micoli.micraft.game.equipment

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.micoli.micraft.config.isYamlEffectivelyEmpty
import org.micoli.micraft.config.mergeMapConfig
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.config.yamlMapSection
import org.micoli.micraft.game.world.EquipmentCategory
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(WeaponCategoryRegistryLoader::class.java)

private val ENTRY_MAP_SERIALIZER =
    MapSerializer(String.serializer(), WeaponCategoryYamlEntry.serializer())

class WeaponCategoryRegistryLoader(
    private val path: Path,
    private val resourcesPath: Path = Path.of("resources/config/weapons.yaml"),
) {
    private val default: Map<String, WeaponCategoryYamlEntry> =
        Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, resourcesPath.readText())

    init {
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(spliceMissingAsComments("", yamlMapSection(default, null)))
            log.info("Generated default weapon category registry at {}", path.toAbsolutePath())
        } else {
            runCatching { Yaml.default.parseToYamlNode(originalText) }
                .onSuccess { node ->
                    path.writeText(
                        spliceMissingAsComments(
                            originalText, yamlMapSection(mergedEntries(node), node)))
                }
                .onFailure {
                    if (!originalText.isYamlEffectivelyEmpty())
                        log.warn(
                            "weapons.yaml has unparseable structure, leaving file untouched: {}",
                            it.message)
                }
        }
        validateYamlConfig(path, "weapons.schema.json")
    }

    private fun mergedEntries(node: YamlNode?): Map<String, WeaponCategoryYamlEntry> {
        val originalText = if (path.exists()) path.readText() else ""
        val decoded =
            if (originalText.isBlank()) emptyMap()
            else
                runCatching { Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, originalText) }
                    .getOrElse { emptyMap() }
        return mergeMapConfig(decoded, default, node)
    }

    fun load(): Map<EquipmentCategory, WeaponCategoryDefinition> {
        val originalText = if (path.exists()) path.readText() else ""
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        val raw = mergedEntries(node)
        val result =
            raw.mapNotNull { (key, entry) ->
                    runCatching { EquipmentCategory.valueOf(key) }
                        .getOrNull()
                        ?.let {
                            it to
                                WeaponCategoryDefinition(
                                    allowedClasses = entry.allowedClasses,
                                    mainHandOnly = entry.mainHandOnly)
                        }
                }
                .toMap()
        log.info("Weapon category registry loaded: {} categories", result.size)
        return result
    }
}
