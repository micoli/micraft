package org.micoli.micraft.game.rpg

import java.nio.file.Path
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ExperienceConfig::class.java)

class ExperienceConfig(
    private val path: Path = ConfigPaths.dataConfig("experience.yaml"),
    private val resourcesPath: Path = ConfigPaths.resourcesConfig("experience.yaml"),
) {
    @Volatile
    var data: ExperienceConfigData = ExperienceConfigData()
        private set

    init {
        data = load()
        log.info("Experience config loaded: {}", data)
    }

    private fun load(): ExperienceConfigData =
        loadOverridableConfig("experience.yaml", OverridablePaths(resourcesPath, path))

    fun reload(): ExperienceConfigData {
        data = load()
        log.info("Experience config reloaded: {}", data)
        return data
    }
}
