package org.micoli.micraft.game.world.vegetation

import java.nio.file.Path
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(VegetationConfig::class.java)

class VegetationConfig(
    private val path: Path = ConfigPaths.dataConfig("vegetation.yaml"),
    private val resourcesPath: Path = ConfigPaths.resourcesConfig("vegetation.yaml"),
) {
    @Volatile
    var data: VegetationConfigData = VegetationConfigData()
        private set

    init {
        data = load()
        log.info("Vegetation config loaded: {} chains", data.chains.size)
    }

    private fun load(): VegetationConfigData =
        loadOverridableConfig("vegetation.yaml", OverridablePaths(resourcesPath, path))

    fun reload(): VegetationConfigData {
        data = load()
        log.info("Vegetation config reloaded: {} chains", data.chains.size)
        return data
    }
}
