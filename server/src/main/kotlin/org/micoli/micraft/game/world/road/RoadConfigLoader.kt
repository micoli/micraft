package org.micoli.micraft.game.world.road

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

private val log = LoggerFactory.getLogger("RoadConfigLoader")

fun loadRoadConfig(path: Path, resourcesPath: Path): RoadConfig {
    val default = Yaml.default.decodeFromString(RoadConfig.serializer(), resourcesPath.readText())
    val originalText = if (path.exists()) path.readText() else ""
    path.parent?.createDirectories()
    if (originalText.isBlank()) {
        log.info("No roads.yaml found at {} — creating with defaults", path.toAbsolutePath())
        path.writeText(
            spliceMissingAsComments("", yamlConfigSection(RoadConfig::class, "", default, null)))
        return default
    }
    val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
    if (node == null) {
        if (!originalText.isYamlEffectivelyEmpty())
            log.warn("roads.yaml has unparseable structure, leaving file untouched")
        return default
    }
    val decoded =
        runCatching { Yaml.default.decodeFromString(RoadConfig.serializer(), originalText) }
            .getOrElse { e ->
                log.warn("Failed to load roads.yaml ({}) — using defaults", e.message)
                default
            }
    val merged = mergeConfig(RoadConfig::class, decoded, default, node)
    log.info(
        "Roads loaded: enabled={} | defaultWidth={} | voronoiCellSize={} | biomes=[{}]",
        merged.enabled,
        merged.defaultRoad.width,
        merged.voronoiCellSize,
        merged.biomes.keys.joinToString(),
    )
    path.writeText(
        spliceMissingAsComments(
            originalText, yamlConfigSection(RoadConfig::class, "", merged, node)))
    return merged
}
