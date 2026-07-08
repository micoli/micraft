package org.micoli.micraft.rpg

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

private val log = LoggerFactory.getLogger("ExperienceConfig")

@Serializable
data class ProgressionConfig(
    val thresholds: List<Int> =
        listOf(
            300,
            900,
            2700,
            6500,
            11700,
            21060,
            37908,
            68234,
            122821,
            171950,
            240730,
            337022,
            471831,
            660563,
            924789,
            1294704,
            1812586,
            2537620,
            3552668),
)

@Serializable
data class SourcesConfig(
    val commonPerLevel: Int = 50,
    val elitePerLevel: Int = 200,
    val bossPerLevel: Int = 1000,
)

@Serializable
data class XpGroupConfig(
    val enabled: Boolean = true,
    val bonusPerMember: Double = 0.10,
)

@Serializable
data class ExperienceConfigData(
    val progression: ProgressionConfig = ProgressionConfig(),
    val sources: SourcesConfig = SourcesConfig(),
    val group: XpGroupConfig = XpGroupConfig(),
)

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
