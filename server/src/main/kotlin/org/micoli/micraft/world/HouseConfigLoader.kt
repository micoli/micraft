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

private val DEFAULT_YAML =
    """
enabled: true
gridCellSize: 48
clusterCheckRadius: 2
floorHeight: 4
maxHouseSize: 20

houseTypes:
  - id: cabin
    widthMin: 5
    widthMax: 8
    depthMin: 5
    depthMax: 8
    floorsMin: 1
    floorsMax: 1
    roofTypes: [gabled]
    roomsMin: 2
    roomsMax: 4
    doorsMin: 1
    doorsMax: 1

  - id: house
    widthMin: 7
    widthMax: 12
    depthMin: 7
    depthMax: 12
    floorsMin: 1
    floorsMax: 2
    roofTypes: [flat, gabled]
    roomsMin: 3
    roomsMax: 6
    doorsMin: 1
    doorsMax: 2

  - id: townhouse
    widthMin: 5
    widthMax: 8
    depthMin: 8
    depthMax: 15
    floorsMin: 2
    floorsMax: 3
    roofTypes: [flat]
    roomsMin: 4
    roomsMax: 8
    doorsMin: 1
    doorsMax: 1

defaultBiome:
  wallBlock: STONE
  roofBlock: STONE
  floorBlock: STONE
  houseProbability: 0.0
  clusterBonus: 0.0
  typeRates: {}

biomes:
  plains:
    wallBlock: OAK_LOG
    roofBlock: OAK_LOG
    floorBlock: DIRT
    houseProbability: 0.15
    clusterBonus: 0.25
    typeRates:
      cabin: 0.3
      house: 0.5
      townhouse: 0.2
  desert:
    wallBlock: SANDSTONE
    roofBlock: SANDSTONE
    floorBlock: SANDSTONE
    houseProbability: 0.08
    clusterBonus: 0.10
    typeRates:
      cabin: 0.6
      house: 0.4
  forest:
    wallBlock: OAK_LOG
    roofBlock: OAK_LOG
    floorBlock: DIRT
    houseProbability: 0.04
    clusterBonus: 0.05
    typeRates:
      cabin: 1.0
  tundra:
    wallBlock: STONE
    roofBlock: SNOW
    floorBlock: STONE
    houseProbability: 0.05
    clusterBonus: 0.10
    typeRates:
      cabin: 1.0
  mountains:
    wallBlock: STONE
    roofBlock: STONE
    floorBlock: STONE
    houseProbability: 0.0
    clusterBonus: 0.0
    typeRates: {}
"""
        .trimIndent()

fun loadHouseConfig(path: Path): HouseConfig {
    if (!path.exists()) {
        log.info("No houses.yaml found at {} — creating with defaults", path.toAbsolutePath())
        path.parent?.createDirectories()
        path.writeText(DEFAULT_YAML)
        return Yaml.default.decodeFromString(HouseConfig.serializer(), DEFAULT_YAML)
    }
    return runCatching {
            val config = Yaml.default.decodeFromString(HouseConfig.serializer(), path.readText())
            log.info(
                "Houses loaded: enabled={} | gridCellSize={} | types=[{}] | biomes=[{}]",
                config.enabled,
                config.gridCellSize,
                config.houseTypes.joinToString { it.id },
                config.biomes.keys.joinToString(),
            )
            config
        }
        .getOrElse { e ->
            log.warn("Failed to load houses.yaml ({}) — using defaults", e.message)
            Yaml.default.decodeFromString(HouseConfig.serializer(), DEFAULT_YAML)
        }
}
