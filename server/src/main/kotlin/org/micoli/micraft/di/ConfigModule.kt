package org.micoli.micraft.di

import java.nio.file.Path
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.micoli.micraft.config.validateAlli18nYamlConfigs
import org.micoli.micraft.configDir
import org.micoli.micraft.dataPath
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.ServerConfig
import org.micoli.micraft.game.applyServerConfig
import org.micoli.micraft.game.keybinding.loadKeyBindings
import org.micoli.micraft.game.loadServerConfig
import org.micoli.micraft.resourcesConfigDir

@Module
class ConfigModule {
    @Single(createdAtStart = true)
    fun i18nBootstrap(): I18nBootstrapResult {
        validateAlli18nYamlConfigs(configDir)
        return I18nBootstrapResult()
    }

    @Single
    fun serverConfig(): ServerConfig =
        loadServerConfig(
                Path.of("$dataPath/config/server.yaml"), resourcesConfigDir.resolve("server.yaml"))
            .also { applyServerConfig(it) }

    @Single fun gameConfig(serverConfig: ServerConfig): GameConfig = serverConfig.game

    @Single(createdAtStart = true)
    fun keyBindingsBootstrap(): KeyBindingsBootstrapResult {
        loadKeyBindings(Path.of("$dataPath/config/keybindings.yaml"))
        return KeyBindingsBootstrapResult()
    }
}
