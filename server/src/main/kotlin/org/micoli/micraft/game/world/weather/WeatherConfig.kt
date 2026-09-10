package org.micoli.micraft.game.world.weather

import java.nio.file.Path
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(WeatherConfig::class.java)

class WeatherConfig(
    private val path: Path = ConfigPaths.dataConfig("weather.yaml"),
    private val resourcesPath: Path = ConfigPaths.resourcesConfig("weather.yaml"),
) {
    @Volatile
    var data: WeatherConfigData = WeatherConfigData()
        private set

    init {
        data = load()
        log.info("Weather config loaded: {} weather types", data.weatherTypes.size)
    }

    private fun load(): WeatherConfigData =
        loadOverridableConfig("weather.yaml", OverridablePaths(resourcesPath, path))

    fun reload(): WeatherConfigData {
        data = load()
        log.info("Weather config reloaded: {} weather types", data.weatherTypes.size)
        return data
    }

    internal fun update(fn: (WeatherConfigData) -> WeatherConfigData) {
        data = fn(data)
    }
}
