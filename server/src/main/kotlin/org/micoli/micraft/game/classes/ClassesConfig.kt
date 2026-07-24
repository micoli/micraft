package org.micoli.micraft.game.classes

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.micoli.micraft.config.YamlSection
import org.micoli.micraft.config.isYamlEffectivelyEmpty
import org.micoli.micraft.config.mergeConfig
import org.micoli.micraft.config.mergeMapConfig
import org.micoli.micraft.config.presentInYaml
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.yamlConfigSection
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ClassesConfig")

private const val SCHEMA_HEADER = "# yaml-language-server: \$schema=../schemas/classes.schema.json"

class ClassesConfig(
    private val path: Path = Path.of("data/config/classes.yaml"),
    private val resourcesPath: Path = Path.of("resources/config/classes.yaml"),
) {
    @Volatile
    var data: ClassesConfigData = ClassesConfigData()
        private set

    init {
        data = load()
        log.info(
            "Classes config loaded: {} classes, regenIntervalMs={}",
            data.classes.size,
            data.regen.regenIntervalMs)
    }

    private fun buildYamlSection(config: ClassesConfigData, node: YamlNode?): YamlSection {
        val classesNode: YamlNode? = (node as? YamlMap)?.get("classes")
        return YamlSection(
            key = "",
            subsections =
                listOf(
                    yamlConfigSection(RegenSettings::class, "regen", config.regen, node),
                    YamlSection(
                        key = "classes",
                        present = presentInYaml(node, "classes"),
                        subsections =
                            config.classes.map { (name, entry) ->
                                yamlConfigSection(
                                    ClassDefinitionEntry::class, name, entry, classesNode)
                            },
                    ),
                ),
        )
    }

    private fun load(): ClassesConfigData {
        val default =
            Yaml.default.decodeFromString(ClassesConfigData.serializer(), resourcesPath.readText())
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()

        if (originalText.isBlank()) {
            path.writeText(
                SCHEMA_HEADER + "\n" + spliceMissingAsComments("", buildYamlSection(default, null)))
            log.info("Generated default classes config at {}", path.toAbsolutePath())
            return default
        }

        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        if (node == null) {
            if (!originalText.isYamlEffectivelyEmpty()) log.warn("classes.yaml has unparseable structure, leaving file untouched")
            return default
        }

        val decoded =
            runCatching {
                    Yaml.default.decodeFromString(ClassesConfigData.serializer(), originalText)
                }
                .getOrElse { e ->
                    log.warn("Failed to load classes.yaml ({}), using defaults", e.message)
                    default
                }

        val classesNode: YamlNode? = (node as? YamlMap)?.get("classes")
        val mergedRegen =
            mergeConfig(RegenSettings::class, decoded.regen, default.regen, node, listOf("regen"))
        val mergedClasses =
            mergeMapConfig<ClassDefinitionEntry>(decoded.classes, default.classes, classesNode)
        val merged = ClassesConfigData(regen = mergedRegen, classes = mergedClasses)

        path.writeText(spliceMissingAsComments(originalText, buildYamlSection(merged, node)))
        return merged
    }

    fun reload(): ClassesConfigData {
        data = load()
        log.info("Classes config reloaded: {} classes", data.classes.size)
        return data
    }
}
