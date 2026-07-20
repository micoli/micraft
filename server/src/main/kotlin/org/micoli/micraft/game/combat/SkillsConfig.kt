package org.micoli.micraft.game.combat

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

private val log = LoggerFactory.getLogger("SkillsConfig")

private const val SCHEMA_HEADER = "# yaml-language-server: \$schema=../schemas/skills.schema.json"

class SkillsConfig(
    private val path: Path = Path.of("data/config/skills.yaml"),
    private val resourcesPath: Path = Path.of("resources/config/skills.yaml"),
) {
    @Volatile
    var data: SkillsConfigData = SkillsConfigData()
        private set

    init {
        data = load()
        log.info("Skills config loaded: {} attacks, {} spells", data.attacks.size, data.spells.size)
    }

    private fun load(): SkillsConfigData {
        val default =
            Yaml.default.decodeFromString(SkillsConfigData.serializer(), resourcesPath.readText())
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(
                SCHEMA_HEADER +
                    "\n" +
                    spliceMissingAsComments(
                        "", yamlConfigSection(SkillsConfigData::class, "", default, null)))
            log.info("Generated default skills config at {}", path.toAbsolutePath())
            return default
        }
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        if (node == null) {
            log.warn("skills.yaml has unparseable structure, leaving file untouched")
            return default
        }
        val decoded =
            runCatching {
                    Yaml.default.decodeFromString(SkillsConfigData.serializer(), originalText)
                }
                .getOrElse { e ->
                    log.warn("Failed to load skills.yaml ({}), using defaults", e.message)
                    default
                }
        val merged = mergeConfig(SkillsConfigData::class, decoded, default, node)
        path.writeText(
            spliceMissingAsComments(
                originalText, yamlConfigSection(SkillsConfigData::class, "", merged, node)))
        return merged
    }

    fun reload(): SkillsConfigData {
        data = load()
        log.info("Skills config reloaded: {} attacks, {} spells", data.attacks.size, data.spells.size)
        return data
    }
}
