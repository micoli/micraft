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

private const val SCHEMA_HEADER = "# yaml-language-server: \$schema=../schemas/weather.schema.json"

class WeatherConfig(
    private val path: Path = Path.of("data/config/weather.yaml"),
    private val resourcesPath: Path = Path.of("resources/config/weather.yaml"),
) {
    @Volatile
    var data: WeatherConfigData = WeatherConfigData()
        private set

    init {
        validateYamlConfig(path, "weather.schema.json")
        data = load()
        log.info("Weather config loaded: {} weather types", data.weatherTypes.size)
    }

    private fun load(): WeatherConfigData {
        val default =
            Yaml.default.decodeFromString(WeatherConfigData.serializer(), resourcesPath.readText())
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(
                SCHEMA_HEADER +
                    "\n" +
                    spliceMissingAsComments(
                        "", yamlConfigSection(WeatherConfigData::class, "", default, null)))
            log.info("Generated default weather config at {}", path.toAbsolutePath())
            return default
        }
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        if (node == null) {
            log.warn("weather.yaml has unparseable structure, leaving file untouched")
            return default
        }
        val decoded =
            runCatching {
                    Yaml.default.decodeFromString(WeatherConfigData.serializer(), originalText)
                }
                .getOrElse { e ->
                    log.warn("Failed to load weather.yaml ({}), using defaults", e.message)
                    default
                }
        val merged = mergeConfig(WeatherConfigData::class, decoded, default, node)
        path.writeText(
            spliceMissingAsComments(
                originalText, yamlConfigSection(WeatherConfigData::class, "", merged, node)))
        return merged
    }

    fun reload(): WeatherConfigData {
        data = load()
        log.info("Weather config reloaded: {} weather types", data.weatherTypes.size)
        return data
    }

    internal fun update(fn: (WeatherConfigData) -> WeatherConfigData) {
        data = fn(data)
    }
}
