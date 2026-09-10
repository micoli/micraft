package org.micoli.micraft.game.world.house

import java.nio.file.Path
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("HouseConfigLoader")

fun loadHouseConfig(
    path: Path = ConfigPaths.dataConfig("houses.yaml"),
    resourcesPath: Path = ConfigPaths.resourcesConfig("houses.yaml"),
): HouseConfig {
    val merged =
        loadOverridableConfig<HouseConfig>("houses.yaml", OverridablePaths(resourcesPath, path))
    log.info(
        "Houses loaded: enabled={} | gridCellSize={} | types=[{}] | biomes=[{}]",
        merged.enabled,
        merged.gridCellSize,
        merged.houseTypes.joinToString { it.id },
        merged.biomes.keys.joinToString(),
    )
    return merged
}
