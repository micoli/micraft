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

private val log = LoggerFactory.getLogger(ToolRegistryLoader::class.java)

private fun ToolYamlEntry.applyOverride(o: ToolYamlOverride) =
    copy(
        category = o.category ?: category,
        breakSpeedMultiplier = o.breakSpeedMultiplier ?: breakSpeedMultiplier,
        statBonus = o.statBonus ?: statBonus,
        rotate = o.rotate ?: rotate)

class ToolRegistryLoader(
    private val toolsPath: Path,
    private val dataToolsPath: Path,
) {
    fun load(): Map<String, ToolDefinition> {
        if (!toolsPath.exists()) return emptyMap()
        val result =
            toolsPath
                .listDirectoryEntries()
                .filter { it.isDirectory() }
                .mapNotNull { dir ->
                    val name = dir.fileName.toString()
                    val yaml = dir.resolve("$name.yaml")
                    if (!yaml.exists()) return@mapNotNull null
                    runCatching {
                            Yaml.default.decodeFromString(
                                ToolYamlEntry.serializer(), yaml.readText())
                        }
                        .onFailure { log.warn("Failed to load tool '{}': {}", name, it.message) }
                        .getOrNull()
                        ?.let { entry ->
                            val dataYaml = dataToolsPath.resolve("$name/$name.yaml")
                            val merged =
                                if (dataYaml.exists()) {
                                    val content = dataYaml.readText()
                                    val overrideResult =
                                        if (content.isNotBlank()) {
                                            runCatching {
                                                Yaml.default.decodeFromString(
                                                    ToolYamlOverride.serializer(), content)
                                            }
                                        } else Result.success(ToolYamlOverride())
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
                                                            ToolYamlEntry::class,
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
                                ToolDefinition(
                                    category = merged.category,
                                    breakSpeedMultiplier = merged.breakSpeedMultiplier,
                                    statBonus = merged.statBonus,
                                    rotate = merged.rotate)
                        }
                }
                .toMap()
        log.info("Tool registry loaded: {} tool types", result.size)
        return result
    }
}
