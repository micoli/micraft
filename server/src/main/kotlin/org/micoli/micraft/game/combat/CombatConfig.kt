package org.micoli.micraft.game.combat

import java.nio.file.Path
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.OverridablePaths
import org.micoli.micraft.config.loadOverridableConfig
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(CombatConfig::class.java)

class CombatConfig(
    private val path: Path = ConfigPaths.dataConfig("combat.yaml"),
    private val resourcesPath: Path = ConfigPaths.resourcesConfig("combat.yaml"),
) {
    @Volatile
    var data: CombatConfigData = CombatConfigData()
        private set

    init {
        data = load()
        log.info("Combat config loaded: {}", data)
    }

    private fun load(): CombatConfigData =
        loadOverridableConfig("combat.yaml", OverridablePaths(resourcesPath, path))

    fun reload(): CombatConfigData {
        data = load()
        log.info("Combat config reloaded: {}", data)
        return data
    }
}
