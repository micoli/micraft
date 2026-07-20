package org.micoli.micraft.game.combat

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.KSerializer
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.config.mergeConfig
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.yamlConfigSection
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SkillsConfig")

private const val ATTACK_SCHEMA_HEADER =
    "# yaml-language-server: \$schema=../../schemas/skill-attack.schema.json"
private const val SPELL_SCHEMA_HEADER =
    "# yaml-language-server: \$schema=../../schemas/skill-spell.schema.json"

class SkillsConfig(
    private val resourcesRoot: Path = Path.of("resources/config/skills"),
    private val dataRoot: Path = Path.of("data/config/skills"),
) {
    @Volatile
    var data: SkillsConfigData = SkillsConfigData()
        private set

    init {
        data = load()
        log.info("Skills config loaded: {} attacks, {} spells", data.attacks.size, data.spells.size)
    }

    private fun load(): SkillsConfigData {
        val attacks =
            loadEntries(
                resourcesDir = resourcesRoot.resolve("attacks"),
                dataDir = dataRoot.resolve("attacks"),
                schemaHeader = ATTACK_SCHEMA_HEADER,
                serializer = AttackDefinition.serializer(),
                isEnabled = { it.enabled },
            )
        val spells =
            loadEntries(
                resourcesDir = resourcesRoot.resolve("spells"),
                dataDir = dataRoot.resolve("spells"),
                schemaHeader = SPELL_SCHEMA_HEADER,
                serializer = SpellDefinition.serializer(),
                isEnabled = { it.enabled },
            )
        return SkillsConfigData(attacks = attacks, spells = spells)
    }

    private inline fun <reified T : Any> loadEntries(
        resourcesDir: Path,
        dataDir: Path,
        schemaHeader: String,
        serializer: KSerializer<T>,
        crossinline isEnabled: (T) -> Boolean,
    ): Map<String, T> {
        if (!resourcesDir.exists() || !resourcesDir.isDirectory()) return emptyMap()
        dataDir.createDirectories()
        val result = mutableMapOf<String, T>()
        for (resourceFile in resourcesDir.listDirectoryEntries("*.yaml").sorted()) {
            val name = resourceFile.fileName.toString().removeSuffix(".yaml")
            val default =
                runCatching { Yaml.default.decodeFromString(serializer, resourceFile.readText()) }
                    .onFailure { e ->
                        log.warn("Failed to load resource {}.yaml ({}), skipping", name, e.message)
                    }
                    .getOrNull() ?: continue
            val dataFile = dataDir.resolve("$name.yaml")
            val originalText = if (dataFile.exists()) dataFile.readText() else ""
            val merged =
                if (originalText.isBlank()) {
                    dataFile.writeText(
                        schemaHeader +
                            "\n" +
                            spliceMissingAsComments(
                                "", yamlConfigSection(T::class, "", default, null)))
                    default
                } else {
                    val node =
                        runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
                    if (node == null) {
                        log.warn(
                            "data/{}.yaml has unparseable structure, using resource default", name)
                        default
                    } else {
                        val decoded =
                            runCatching { Yaml.default.decodeFromString(serializer, originalText) }
                                .getOrElse { e ->
                                    log.warn(
                                        "Failed to decode data/{}.yaml ({}), using default",
                                        name,
                                        e.message)
                                    default
                                }
                        val m = mergeConfig(T::class, decoded, default, node)
                        dataFile.writeText(
                            spliceMissingAsComments(
                                originalText, yamlConfigSection(T::class, "", m, node)))
                        m
                    }
                }
            if (isEnabled(merged)) result[name] = merged
            else log.debug("Skill {} is disabled, skipping", name)
        }
        return result
    }

    fun reload(): SkillsConfigData {
        data = load()
        log.info(
            "Skills config reloaded: {} attacks, {} spells", data.attacks.size, data.spells.size)
        return data
    }
}
