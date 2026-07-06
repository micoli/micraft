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

private const val SCHEMA_HEADER =
    "# yaml-language-server: \$schema=../schemas/vegetation.schema.json"

class VegetationConfig(
    private val path: Path = Path.of("data/config/vegetation.yaml"),
    private val resourcesPath: Path = Path.of("resources/config/vegetation.yaml"),
) {
    @Volatile
    var data: VegetationConfigData = VegetationConfigData()
        private set

    init {
        data = load()
        log.info("Vegetation config loaded: {} chains", data.chains.size)
    }

    private fun load(): VegetationConfigData {
        val default =
            Yaml.default.decodeFromString(
                VegetationConfigData.serializer(), resourcesPath.readText())
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(
                SCHEMA_HEADER +
                    "\n" +
                    spliceMissingAsComments(
                        "", yamlConfigSection(VegetationConfigData::class, "", default, null)))
            log.info("Generated default vegetation config at {}", path.toAbsolutePath())
            return default
        }
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        if (node == null) {
            log.warn("vegetation.yaml has unparseable structure, leaving file untouched")
            return default
        }
        val decoded =
            runCatching {
                    Yaml.default.decodeFromString(VegetationConfigData.serializer(), originalText)
                }
                .getOrElse { e ->
                    log.warn("Failed to load vegetation.yaml ({}), using defaults", e.message)
                    default
                }
        val merged = mergeConfig(VegetationConfigData::class, decoded, default, node)
        path.writeText(
            spliceMissingAsComments(
                originalText, yamlConfigSection(VegetationConfigData::class, "", merged, node)))
        return merged
    }

    fun reload(): VegetationConfigData {
        data = load()
        log.info("Vegetation config reloaded: {} chains", data.chains.size)
        return data
    }
}
