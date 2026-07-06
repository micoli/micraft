package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.micoli.micraft.world.proceduralGenerator.house.HouseConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("HouseConfigLoader")

fun loadHouseConfig(path: Path, resourcesPath: Path): HouseConfig {
    val default = Yaml.default.decodeFromString(HouseConfig.serializer(), resourcesPath.readText())
    val originalText = if (path.exists()) path.readText() else ""
    path.parent?.createDirectories()
    if (originalText.isBlank()) {
        log.info("No houses.yaml found at {} — creating with defaults", path.toAbsolutePath())
        path.writeText(
            spliceMissingAsComments("", yamlConfigSection(HouseConfig::class, "", default, null)))
        return default
    }
    validateYamlConfig(path, "houses.schema.json")
    val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
    if (node == null) {
        log.warn("houses.yaml has unparseable structure, leaving file untouched")
        return default
    }
    val decoded =
        runCatching { Yaml.default.decodeFromString(HouseConfig.serializer(), originalText) }
            .getOrElse { e ->
                log.warn("Failed to load houses.yaml ({}) — using defaults", e.message)
                default
            }
    val merged = mergeConfig(HouseConfig::class, decoded, default, node)
    log.info(
        "Houses loaded: enabled={} | gridCellSize={} | types=[{}] | biomes=[{}]",
        merged.enabled,
        merged.gridCellSize,
        merged.houseTypes.joinToString { it.id },
        merged.biomes.keys.joinToString(),
    )
    path.writeText(
        spliceMissingAsComments(
            originalText, yamlConfigSection(HouseConfig::class, "", merged, node)))
    return merged
}
