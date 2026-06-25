package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.micoli.micraft.world.proceduralGenerator.road.RoadConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("RoadConfigLoader")

private val DEFAULT_YAML =
    """
enabled: true
vegetationAllowedOnRoad: false
minVegetationDistanceFromRoad: 1
voronoiCellSize: 128
displacementScale: 20.0
displacementFrequency: 0.02
defaultRoad:
  width: 3
  surface: GRAVEL
  roadProbability: 0.7
biomes:
  desert:
    width: 5
    surface: SANDSTONE
    roadProbability: 0.5
  plains:
    width: 3
    surface: GRAVEL
    roadProbability: 0.8
  forest:
    width: 2
    surface: DIRT
    roadProbability: 0.6
  tundra:
    width: 3
    surface: SNOW
    roadProbability: 0.4
"""
        .trimIndent()

fun loadRoadConfig(path: Path): RoadConfig {
    if (!path.exists()) {
        log.info("No roads.yaml found at {} — creating with defaults", path.toAbsolutePath())
        path.parent?.createDirectories()
        path.writeText(DEFAULT_YAML)
        return RoadConfig()
    }
    return runCatching {
            val config = Yaml.default.decodeFromString(RoadConfig.serializer(), path.readText())
            log.info(
                "Roads loaded: enabled={} | defaultWidth={} | voronoiCellSize={} | biomes=[{}]",
                config.enabled,
                config.defaultRoad.width,
                config.voronoiCellSize,
                config.biomes.keys.joinToString(),
            )
            config
        }
        .getOrElse { e ->
            log.warn("Failed to load roads.yaml ({}) — using defaults", e.message)
            RoadConfig()
        }
}
