package org.micoli.micraft.game.world.road

import java.nio.file.Path
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("RoadConfigLoader")

fun loadRoadConfig(
    path: Path = ConfigPaths.dataConfig("roads.yaml"),
    resourcesPath: Path = ConfigPaths.resourcesConfig("roads.yaml"),
): RoadConfig {
    val merged =
        loadOverridableConfig<RoadConfig>("roads.yaml", OverridablePaths(resourcesPath, path))
    log.info(
        "Roads loaded: enabled={} | defaultWidth={} | voronoiCellSize={} | biomes=[{}]",
        merged.enabled,
        merged.defaultRoad.width,
        merged.voronoiCellSize,
        merged.biomes.keys.joinToString(),
    )
    return merged
}
