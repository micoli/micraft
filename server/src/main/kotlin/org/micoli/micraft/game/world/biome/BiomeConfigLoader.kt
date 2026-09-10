package org.micoli.micraft.game.world.biome

import java.nio.file.Path
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("BiomeConfigLoader")

fun loadBiomeRegistry(
    path: Path = ConfigPaths.dataConfig("biomes.yaml"),
    resourcesPath: Path = ConfigPaths.resourcesConfig("biomes.yaml"),
): BiomeRegistry {
    val merged =
        loadOverridableConfig<BiomeConfig>(
            "biomes.yaml", OverridablePaths(resourcesPath, path), "biomes.schema.json")
    log.info(
        "Biomes loaded: [{}] | voronoiCellSize={} blendRadius={}",
        merged.biomes.joinToString { it.id },
        merged.voronoiCellSize,
        merged.voronoiBlendRadius,
    )
    return BiomeRegistry.from(merged)
}
