package org.micoli.micraft.world

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("VegetationConfig")

@Serializable
data class GrowthStage(
    val block: String,
    val minTicks: Int,
    val maxTicks: Int,
)

@Serializable
data class GrowthChain(
    val name: String,
    val stages: List<GrowthStage>,
    val finalTree: String,
    val requiresVegetationHost: Boolean = true,
)

@Serializable
data class VegetationConfigData(
    val enabled: Boolean = true,
    val growthCheckIntervalTicks: Int = 40,
    val chains: List<GrowthChain> = emptyList(),
)

private val DEFAULT_YAML =
    """
# yaml-language-server: ${'$'}schema=../schemas/vegetation.schema.json
enabled: true
growthCheckIntervalTicks: 40

chains:
  - name: oak_growth
    stages:
      - block: SEED
        minTicks: 400
        maxTicks: 1200
      - block: SPROUT
        minTicks: 600
        maxTicks: 2000
      - block: SAPLING
        minTicks: 800
        maxTicks: 3000
    finalTree: OAK_TREE
    requiresVegetationHost: true

  - name: pine_growth
    stages:
      - block: SEED
        minTicks: 500
        maxTicks: 1500
      - block: SPROUT
        minTicks: 700
        maxTicks: 2200
      - block: SAPLING
        minTicks: 1000
        maxTicks: 3500
    finalTree: PINE_TREE
    requiresVegetationHost: true
"""
        .trimIndent()

class VegetationConfig(private val path: Path = Path.of("data/config/vegetation.yaml")) {
    @Volatile
    var data: VegetationConfigData = VegetationConfigData()
        private set

    init {
        if (!path.exists()) {
            path.parent.createDirectories()
            path.writeText(DEFAULT_YAML)
            log.info("Generated default vegetation config at {}", path.toAbsolutePath())
        }
        data = parse()
        log.info("Vegetation config loaded: {} chains", data.chains.size)
    }

    private fun parse(): VegetationConfigData =
        runCatching {
                Yaml.default.decodeFromString(VegetationConfigData.serializer(), path.readText())
            }
            .getOrElse { e ->
                log.warn("Failed to load vegetation.yaml ({}), using defaults", e.message)
                Yaml.default.decodeFromString(VegetationConfigData.serializer(), DEFAULT_YAML)
            }

    fun reload(): VegetationConfigData {
        data = parse()
        log.info("Vegetation config reloaded: {} chains", data.chains.size)
        return data
    }
}
