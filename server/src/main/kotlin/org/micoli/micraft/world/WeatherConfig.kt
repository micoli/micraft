package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("WeatherConfig")

@Serializable
data class WeatherTypeConfig(
    val type: String,
    val biomes: List<String>,
    val enabled: Boolean = true,
    val spawnRatePerBiomeTick: Double = 0.0002,
    val minDurationTicks: Long = 1200,
    val maxDurationTicks: Long = 12000,
    val minRadius: Float = 48f,
    val maxRadius: Float = 192f,
    val driftSpeed: Float = 0.1f,
)

@Serializable
data class WeatherConfigData(
    val enabled: Boolean = true,
    val weatherTypes: List<WeatherTypeConfig> = emptyList(),
)

private val DEFAULT_YAML =
    """
# yaml-language-server: ${'$'}schema=../schemas/weather.schema.json
enabled: true
weatherTypes:
  - type: RAIN
    biomes: [plains, forest]
    enabled: true
    spawnRatePerBiomeTick: 0.0002
    minDurationTicks: 1200
    maxDurationTicks: 12000
    minRadius: 48.0
    maxRadius: 192.0
    driftSpeed: 0.1
  - type: STORM
    biomes: [plains, forest, mountains]
    enabled: true
    spawnRatePerBiomeTick: 0.00005
    minDurationTicks: 600
    maxDurationTicks: 3600
    minRadius: 32.0
    maxRadius: 128.0
    driftSpeed: 0.15
  - type: SNOW
    biomes: [tundra, mountains]
    enabled: true
    spawnRatePerBiomeTick: 0.0003
    minDurationTicks: 2400
    maxDurationTicks: 24000
    minRadius: 64.0
    maxRadius: 256.0
    driftSpeed: 0.05
  - type: FOG
    biomes: [forest, plains, mountains]
    enabled: true
    spawnRatePerBiomeTick: 0.0001
    minDurationTicks: 3000
    maxDurationTicks: 18000
    minRadius: 32.0
    maxRadius: 96.0
    driftSpeed: 0.02
"""
        .trimIndent()

class WeatherConfig(private val path: Path = Path.of("data/weather/weather.yaml")) {
    @Volatile
    var data: WeatherConfigData = WeatherConfigData()
        private set

    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(DEFAULT_YAML)
            log.info("Generated default weather config at {}", path.toAbsolutePath())
        }
        data = parse()
        log.info("Weather config loaded: {} weather types", data.weatherTypes.size)
    }

    private fun parse(): WeatherConfigData =
        runCatching {
                Yaml.default.decodeFromString(WeatherConfigData.serializer(), path.readText())
            }
            .getOrElse { e ->
                log.warn("Failed to load weather.yaml ({}), using defaults", e.message)
                Yaml.default.decodeFromString(WeatherConfigData.serializer(), DEFAULT_YAML)
            }

    fun reload(): WeatherConfigData {
        data = parse()
        log.info("Weather config reloaded: {} weather types", data.weatherTypes.size)
        return data
    }
}
