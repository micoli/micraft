package org.micoli.micraft.di

import java.nio.file.Path
import org.micoli.micraft.RECONCILE_TOLERANCE_XZ
import org.micoli.micraft.RECONCILE_TOLERANCE_Y
import org.micoli.micraft.npc.NpcConfigLoader
import org.micoli.micraft.npc.NpcManager
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.tick.VegetationManager
import org.micoli.micraft.world.DropConfig
import org.micoli.micraft.world.I18nConfig
import org.micoli.micraft.world.NpcRegistryLoader
import org.micoli.micraft.world.VegetationConfig
import org.micoli.micraft.world.WeatherConfig
import org.micoli.micraft.world.WeatherManager
import org.micoli.micraft.world.WorldState
import org.micoli.micraft.world.proceduralGenerator.chunkGenerator.ChunkGenerator

/**
 * Extracted from [org.micoli.micraft.GameLoop.reload] so `/reload` behavior can be tested in
 * isolation.
 */
class ReloadCoordinator(
    private val dropConfig: DropConfig,
    private val world: WorldState,
    private val reloadBiomes: (() -> ChunkGenerator)?,
    private val reloadRegistries: (() -> Unit)?,
    private val reloadGameConfig: (() -> Unit)?,
    private val sessionRegistry: SessionRegistry,
    private val buildRegistrySync: () -> ServerMessage.RegistrySync,
    private val npcConfigLoader: NpcConfigLoader,
    private val npcRegistryLoader: NpcRegistryLoader,
    private val npcManager: NpcManager,
    private val i18n: I18nConfig,
    private val weatherManager: WeatherManager,
    private val vegetationManager: VegetationManager,
) {
    suspend fun reload(lang: String): String {
        val lines = mutableListOf<String>()
        val dropCount = dropConfig.reload()
        lines += i18n.t(lang, "reload:server:drops", dropCount)
        if (reloadBiomes != null) {
            world.generator = reloadBiomes.invoke()
            lines += i18n.t(lang, "reload:server:biomes")
        }
        if (reloadRegistries != null) {
            reloadRegistries.invoke()
            val registrySync = buildRegistrySync()
            sessionRegistry.all().forEach { it.send(registrySync) }
            lines += i18n.t(lang, "reload:server:registry")
        }
        if (reloadGameConfig != null) {
            reloadGameConfig.invoke()
            val configSync =
                ServerMessage.GameConfigSync(RECONCILE_TOLERANCE_XZ, RECONCILE_TOLERANCE_Y)
            sessionRegistry.all().forEach { it.send(configSync) }
            lines += i18n.t(lang, "reload:server:game_config")
        }
        npcConfigLoader.reload()
        npcManager.reloadDefinitions(npcRegistryLoader.reload())
        lines += i18n.t(lang, "reload:server:npc")
        i18n.reload()
        lines += i18n.t(lang, "reload:server:i18n", i18n.locales.size)
        val newWeatherConfig = WeatherConfig()
        weatherManager.reload(newWeatherConfig)
        lines += i18n.t(lang, "reload:server:weather", newWeatherConfig.data.weatherTypes.size)
        val newVegetationConfig = VegetationConfig(Path.of("data/config/vegetation.yaml"))
        vegetationManager.reload(newVegetationConfig)
        lines += i18n.t(lang, "reload:server:vegetation", newVegetationConfig.data.chains.size)
        return lines.joinToString(", ")
    }
}
