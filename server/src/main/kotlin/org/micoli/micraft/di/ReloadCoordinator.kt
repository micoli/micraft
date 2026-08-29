package org.micoli.micraft.di

import java.nio.file.Path
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.MAX_INTERACTION_DISTANCE
import org.micoli.micraft.game.RECONCILE_TOLERANCE_XZ
import org.micoli.micraft.game.RECONCILE_TOLERANCE_Y
import org.micoli.micraft.game.drop.DropConfig
import org.micoli.micraft.game.npc.NpcConfigLoader
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.npc.NpcRegistryLoader
import org.micoli.micraft.game.quest.QuestManager
import org.micoli.micraft.game.quest.QuestRegistryLoader
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.game.world.vegetation.VegetationConfig
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.game.world.weather.WeatherConfig
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.protocol.ServerMessage

/**
 * Extracted from [org.micoli.micraft.game.GameLoop.reload] so `/reload` behavior can be tested in
 * isolation.
 */
class ReloadCoordinator(
    private val dropConfig: DropConfig,
    private val world: WorldState,
    private val reloadBiomes: (() -> ChunkGenerator)?,
    private val reloadRegistries: (() -> Unit)?,
    private val reloadGameConfig: (() -> Unit)?,
    private val reloadFactions: (suspend () -> Unit)? = null,
    private val sessionRegistry: SessionRegistry,
    private val buildRegistrySync: () -> ServerMessage.RegistrySync,
    private val npcConfigLoader: NpcConfigLoader,
    private val npcRegistryLoader: NpcRegistryLoader,
    private val npcManager: NpcManager,
    private val i18n: I18nConfig,
    private val weatherManager: WeatherManager,
    private val vegetationManager: VegetationManager,
    private val questManager: QuestManager? = null,
    private val questRegistryLoader: QuestRegistryLoader? = null,
    private val reloadRbac: (() -> Unit)? = null,
    private val reloadArmorRegistry: (() -> Unit)? = null,
    private val reloadEquipmentCategories: (() -> Unit)? = null,
    private val reloadRecipeRegistry: (() -> Unit)? = null,
    private val reloadCombatSystems: (() -> Unit)? = null,
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
                ServerMessage.GameConfigSync(
                    RECONCILE_TOLERANCE_XZ, RECONCILE_TOLERANCE_Y, MAX_INTERACTION_DISTANCE)
            sessionRegistry.all().forEach { it.send(configSync) }
            lines += i18n.t(lang, "reload:server:game_config")
        }
        if (reloadFactions != null) {
            reloadFactions.invoke()
            lines += i18n.t(lang, "reload:server:factions")
        }
        npcConfigLoader.reload()
        npcManager.reloadDefinitions(npcRegistryLoader.reload())
        lines += i18n.t(lang, "reload:server:npc")
        questRegistryLoader?.load()?.let { questManager?.reloadDefinitions(it) }
        i18n.reload()
        lines += i18n.t(lang, "reload:server:i18n", i18n.locales.size)
        val newWeatherConfig = WeatherConfig()
        weatherManager.reload(newWeatherConfig)
        lines += i18n.t(lang, "reload:server:weather", newWeatherConfig.data.weatherTypes.size)
        val newVegetationConfig = VegetationConfig(Path.of("data/config/vegetation.yaml"))
        vegetationManager.reload(newVegetationConfig)
        lines += i18n.t(lang, "reload:server:vegetation", newVegetationConfig.data.chains.size)
        if (reloadArmorRegistry != null) {
            reloadArmorRegistry.invoke()
            lines += i18n.t(lang, "reload:server:armor")
        }
        if (reloadEquipmentCategories != null) {
            reloadEquipmentCategories.invoke()
            lines += i18n.t(lang, "reload:server:equipment_categories")
        }
        if (reloadRecipeRegistry != null) {
            reloadRecipeRegistry.invoke()
            lines += i18n.t(lang, "reload:server:recipes")
        }
        if (reloadRbac != null) {
            reloadRbac.invoke()
            lines += i18n.t(lang, "reload:server:rbac")
        }
        if (reloadCombatSystems != null) {
            reloadCombatSystems.invoke()
            lines += i18n.t(lang, "reload:server:combat_systems")
        }
        return lines.joinToString(", ")
    }
}
