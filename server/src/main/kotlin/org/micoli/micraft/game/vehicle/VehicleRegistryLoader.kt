package org.micoli.micraft.game.vehicle

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlNode
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.micoli.micraft.config.isYamlEffectivelyEmpty
import org.micoli.micraft.config.mergeMapConfig
import org.micoli.micraft.config.spliceMissingAsComments
import org.micoli.micraft.config.validateYamlConfig
import org.micoli.micraft.config.yamlMapSection
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.vehicle.VehicleDefinition
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(VehicleRegistryLoader::class.java)

private val ENTRY_MAP_SERIALIZER = MapSerializer(String.serializer(), VehicleYamlEntry.serializer())

/** Mirrors [org.micoli.micraft.game.item.ItemRegistryLoader]'s data-dir-override-merge shape. */
class VehicleRegistryLoader(
    private val path: Path,
    private val resourcesPath: Path = Path.of("resources/config/vehicles.yaml"),
    private val modelsPath: Path = Path.of("resources/vehicles"),
    private val dataModelsPath: Path = Path.of("data/resources/vehicles"),
) {
    private val modelLoader = VehicleModelRegistryLoader(modelsPath, dataModelsPath)
    private val default: Map<String, VehicleYamlEntry> =
        Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, resourcesPath.readText())

    init {
        val originalText = if (path.exists()) path.readText() else ""
        path.parent.createDirectories()
        if (originalText.isBlank()) {
            path.writeText(spliceMissingAsComments("", yamlMapSection(default, null)))
            log.info("Generated default vehicle registry at {}", path.toAbsolutePath())
        } else {
            runCatching { Yaml.default.parseToYamlNode(originalText) }
                .onSuccess { node ->
                    path.writeText(
                        spliceMissingAsComments(
                            originalText, yamlMapSection(mergedEntries(node), node)))
                }
                .onFailure {
                    if (!originalText.isYamlEffectivelyEmpty())
                        log.warn(
                            "vehicles.yaml has unparseable structure, leaving file untouched: {}",
                            it.message)
                }
        }
        validateYamlConfig(path, "vehicles.schema.json")
    }

    private fun mergedEntries(node: YamlNode?): Map<String, VehicleYamlEntry> {
        val originalText = if (path.exists()) path.readText() else ""
        val decoded =
            if (originalText.isBlank()) emptyMap()
            else
                runCatching { Yaml.default.decodeFromString(ENTRY_MAP_SERIALIZER, originalText) }
                    .getOrElse { emptyMap() }
        return mergeMapConfig(decoded, default, node)
    }

    fun load(): Map<EntityType, VehicleDefinition> {
        val originalText = if (path.exists()) path.readText() else ""
        val node = runCatching { Yaml.default.parseToYamlNode(originalText) }.getOrNull()
        val raw = mergedEntries(node)
        val result =
            raw.entries.associate { (key, entry) ->
                val model = modelLoader.load(entry.bbmodelFile) ?: VehicleModelDefinition()
                EntityType(key) to
                    VehicleDefinition(
                        bbmodelFile = entry.bbmodelFile,
                        width = entry.width,
                        height = entry.height,
                        speed = model.speed,
                        seatOffset =
                            Vec3(model.seatOffset.x, model.seatOffset.y, model.seatOffset.z),
                    )
            }
        log.info("Vehicle registry loaded: {} vehicle types", result.size)
        return result
    }

    fun reload(): Map<EntityType, VehicleDefinition> = load()
}
