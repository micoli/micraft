package org.micoli.micraft.game.rpg

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.micoli.micraft.config.mergeConfig
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.yamlConfigSection
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ExperienceConfig")

private const val SCHEMA_HEADER =
    "# yaml-language-server: \$schema=../schemas/experience.schema.json"

class ExperienceConfig(
    private val path: Path = Path.of("data/config/experience.yaml"),
    private val resourcesPath: Path = Path.of("resources/config/experience.yaml"),
) {
    @Volatile
    var data: ExperienceConfigData = ExperienceConfigData()
        private set

    init {
        data = load()
        log.info("Experience config loaded: {}", data)
    }

    private fun load(): ExperienceConfigData {
        val default =
            Yaml.default.decodeFromString(
                ExperienceConfigData.serializer(), resourcesPath.readText())
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(
                SCHEMA_HEADER +
                    "\n" +
                    spliceMissingAsComments(
                        "", yamlConfigSection(ExperienceConfigData::class, "", default, null)))
            log.info("Generated default experience config at {}", path.toAbsolutePath())
            return default
        }
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        if (node == null) {
            log.warn("experience.yaml has unparseable structure, leaving file untouched")
            return default
        }
        val decoded =
            runCatching {
                    Yaml.default.decodeFromString(ExperienceConfigData.serializer(), originalText)
                }
                .getOrElse { e ->
                    log.warn("Failed to load experience.yaml ({}), using defaults", e.message)
                    default
                }
        val merged = mergeConfig(ExperienceConfigData::class, decoded, default, node)
        path.writeText(
            spliceMissingAsComments(
                originalText, yamlConfigSection(ExperienceConfigData::class, "", merged, node)))
        return merged
    }

    fun reload(): ExperienceConfigData {
        data = load()
        log.info("Experience config reloaded: {}", data)
        return data
    }
}
