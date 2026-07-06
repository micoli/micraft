package org.micoli.micraft.combat

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import org.micoli.micraft.world.mergeConfig
import org.micoli.micraft.world.spliceMissingAsComments
import org.micoli.micraft.world.yamlConfigSection
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AttackConfig")

@Serializable data class AttackConfigData(val attacks: Map<String, AttackDefinition> = emptyMap())

private const val SCHEMA_HEADER = "# yaml-language-server: \$schema=../schemas/attack.schema.json"

class AttackConfig(
    private val path: Path = Path.of("data/config/attack.yaml"),
    private val resourcesPath: Path = Path.of("resources/config/attack.yaml"),
) {
    @Volatile
    var data: AttackConfigData = AttackConfigData()
        private set

    init {
        data = load()
        log.info("Attack config loaded: {} attacks", data.attacks.size)
    }

    private fun load(): AttackConfigData {
        val default =
            Yaml.default.decodeFromString(AttackConfigData.serializer(), resourcesPath.readText())
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(
                SCHEMA_HEADER +
                    "\n" +
                    spliceMissingAsComments(
                        "", yamlConfigSection(AttackConfigData::class, "", default, null)))
            log.info("Generated default attack config at {}", path.toAbsolutePath())
            return default
        }
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        if (node == null) {
            log.warn("attack.yaml has unparseable structure, leaving file untouched")
            return default
        }
        val decoded =
            runCatching {
                    Yaml.default.decodeFromString(AttackConfigData.serializer(), originalText)
                }
                .getOrElse { e ->
                    log.warn("Failed to load attack.yaml ({}), using defaults", e.message)
                    default
                }
        val merged = mergeConfig(AttackConfigData::class, decoded, default, node)
        path.writeText(
            spliceMissingAsComments(
                originalText, yamlConfigSection(AttackConfigData::class, "", merged, node)))
        return merged
    }

    fun reload(): AttackConfigData {
        data = load()
        log.info("Attack config reloaded: {} attacks", data.attacks.size)
        return data
    }
}
