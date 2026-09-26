package org.micoli.micraft.game.placeable.panel

import java.nio.file.Path
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableDir
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.placeable.panel.PanelDefinition
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(PanelRegistryLoader::class.java)

private fun PanelYamlEntry.applyOverride(o: PanelYamlOverride) =
    copy(
        bbmodelFile = o.bbmodelFile ?: bbmodelFile,
        widthBlocks = o.widthBlocks ?: widthBlocks,
        heightBlocks = o.heightBlocks ?: heightBlocks,
        pixelWidth = o.pixelWidth ?: pixelWidth,
        pixelHeight = o.pixelHeight ?: pixelHeight,
        rotatable = o.rotatable ?: rotatable,
    )

/** Directory-scan loader for panel types — `<name>/<name>.yaml` under `resources/panels`. */
class PanelRegistryLoader(
    private val resourcesPanelsPath: Path = ConfigPaths.resourcesDir("panels"),
    private val dataPanelsPath: Path = ConfigPaths.dataResources("panels"),
) {
    fun load(): Map<EntityType, PanelDefinition> {
        val result =
            loadOverridableDir<PanelYamlEntry, PanelYamlOverride>(
                    paths = OverridablePaths(resourcesPanelsPath, dataPanelsPath),
                    emptyOverride = ::PanelYamlOverride,
                    applyOverride = { entry, override -> entry.applyOverride(override) },
                )
                .entries
                .associate { (key, entry) ->
                    EntityType(key) to
                        PanelDefinition(
                            bbmodelFile = entry.bbmodelFile,
                            widthBlocks = entry.widthBlocks,
                            heightBlocks = entry.heightBlocks,
                            pixelWidth = entry.pixelWidth,
                            pixelHeight = entry.pixelHeight,
                            rotatable = entry.rotatable,
                        )
                }
        log.info("Panel registry loaded: {} types", result.size)
        return result
    }
}
