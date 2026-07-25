package org.micoli.micraft.game.combat

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.micoli.micraft.config.isYamlEffectivelyEmpty
import org.micoli.micraft.config.mergeConfig
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.yamlConfigSection
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("CombatConfig")

private const val SCHEMA_HEADER = "# yaml-language-server: \$schema=../schemas/combat.schema.json"

class CombatConfig(
    private val path: Path = Path.of("data/config/combat.yaml"),
    private val resourcesPath: Path = Path.of("resources/config/combat.yaml"),
) {
    @Volatile
    var data: CombatConfigData = CombatConfigData()
        private set

    init {
        data = load()
        log.info("Combat config loaded: {}", data)
    }

    private fun load(): CombatConfigData {
        val default =
            Yaml.default.decodeFromString(CombatConfigData.serializer(), resourcesPath.readText())
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(
                SCHEMA_HEADER +
                    "\n" +
                    spliceMissingAsComments(
                        "", yamlConfigSection(CombatConfigData::class, "", default, null)))
            log.info("Generated default combat config at {}", path.toAbsolutePath())
            return default
        }
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        if (node == null) {
            if (!originalText.isYamlEffectivelyEmpty())
                log.warn("combat.yaml has unparseable structure, leaving file untouched")
            return default
        }
        val decoded =
            runCatching {
                    Yaml.default.decodeFromString(CombatConfigData.serializer(), originalText)
                }
                .getOrElse { e ->
                    log.warn("Failed to load combat.yaml ({}), using defaults", e.message)
                    default
                }
        val merged = mergeConfig(CombatConfigData::class, decoded, default, node)
        path.writeText(
            spliceMissingAsComments(
                originalText, yamlConfigSection(CombatConfigData::class, "", merged, node)))
        return merged
    }

    fun reload(): CombatConfigData {
        data = load()
        log.info("Combat config reloaded: {}", data)
        return data
    }
}
