package org.micoli.micraft.game.world.biome

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.micoli.micraft.config.mergeConfig
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.config.yamlConfigSection
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("BiomeConfigLoader")

fun loadBiomeRegistry(path: Path, resourcesPath: Path): BiomeRegistry {
    validateYamlConfig(path, "biomes.schema.json")
    val default = Yaml.default.decodeFromString(BiomeConfig.serializer(), resourcesPath.readText())
    val originalText = if (path.exists()) path.readText() else ""
    path.parent?.createDirectories()
    if (originalText.isBlank()) {
        log.warn("No biomes.yaml found at {} — creating with defaults", path.toAbsolutePath())
        path.writeText(
            spliceMissingAsComments("", yamlConfigSection(BiomeConfig::class, "", default, null)))
        return BiomeRegistry.from(default)
    }
    val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
    if (node == null) {
        log.warn("biomes.yaml has unparseable structure, leaving file untouched")
        return BiomeRegistry.from(default)
    }
    val decoded =
        runCatching { Yaml.default.decodeFromString(BiomeConfig.serializer(), originalText) }
            .getOrElse { e ->
                log.warn("Failed to load biomes.yaml ({}), using default", e.message)
                default
            }
    val merged = mergeConfig(BiomeConfig::class, decoded, default, node)
    log.info(
        "Biomes loaded: [{}] | voronoiCellSize={} blendRadius={}",
        merged.biomes.joinToString { it.id },
        merged.voronoiCellSize,
        merged.voronoiBlendRadius,
    )
    path.writeText(
        spliceMissingAsComments(
            originalText, yamlConfigSection(BiomeConfig::class, "", merged, node)))
    return BiomeRegistry.from(merged)
}
