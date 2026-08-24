package org.micoli.micraft.game.vehicle

import com.charleskorn.kaml.Yaml
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("VehicleModelRegistryLoader")

private fun VehicleModelDefinition.applyOverride(o: VehicleModelYamlOverride) =
    copy(speed = o.speed ?: speed, seatOffset = o.seatOffset ?: seatOffset)

/**
 * Loads `resources/vehicles/<name>/<name>.yaml`, optionally overridden by
 * `data/resources/vehicles/<name>/<name>.yaml`. Mirrors
 * [org.micoli.micraft.game.skin.SkinRegistryLoader]'s shape: vehicles without a yaml simply fall
 * back to [VehicleModelDefinition]'s defaults.
 */
class VehicleModelRegistryLoader(
    private val modelsPath: Path,
    private val dataModelsPath: Path,
) {
    fun load(name: String): VehicleModelDefinition? {
        val yaml = modelsPath.resolve("$name/$name.yaml")
        if (!yaml.exists()) return null
        val base =
            runCatching {
                    Yaml.default.decodeFromString(
                        VehicleModelDefinition.serializer(), yaml.readText())
                }
                .onFailure { log.warn("Failed to load vehicle model '{}': {}", name, it.message) }
                .getOrNull() ?: return null

        val dataYaml = dataModelsPath.resolve("$name/$name.yaml")
        if (!dataYaml.exists()) return base
        val content = dataYaml.readText()
        if (content.isBlank()) return base
        return runCatching {
                base.applyOverride(
                    Yaml.default.decodeFromString(VehicleModelYamlOverride.serializer(), content))
            }
            .onFailure {
                log.warn("Failed to apply vehicle model override for '{}': {}", name, it.message)
            }
            .getOrDefault(base)
    }
}
