package org.micoli.micraft.di

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.config.validateAlli18nYamlConfigs
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.ServerConfig
import org.micoli.micraft.game.applyServerConfig
import org.micoli.micraft.game.keybinding.loadKeyBindings
import org.micoli.micraft.game.loadServerConfig

@Module
class ConfigModule {
    @Single(createdAtStart = true)
    fun i18nBootstrap(): I18nBootstrapResult {
        validateAlli18nYamlConfigs(ConfigPaths.dataRoot.resolve("config"))
        return I18nBootstrapResult()
    }

    @Single fun serverConfig(): ServerConfig = loadServerConfig().also { applyServerConfig(it) }

    @Single fun gameConfig(serverConfig: ServerConfig): GameConfig = serverConfig.game

    @Single(createdAtStart = true)
    fun keyBindingsBootstrap(): KeyBindingsBootstrapResult {
        loadKeyBindings(ConfigPaths.dataConfig("keybindings.yaml"))
        return KeyBindingsBootstrapResult()
    }
}
