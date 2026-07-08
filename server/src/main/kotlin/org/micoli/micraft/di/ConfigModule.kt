package org.micoli.micraft.di

import java.nio.file.Path
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.micoli.micraft.config.validateAlli18nYamlConfigs
import org.micoli.micraft.configDir
import org.micoli.micraft.dataPath
import org.micoli.micraft.game.GameConfig
import org.micoli.micraft.game.ServerConfig
import org.micoli.micraft.game.applyServerConfig
import org.micoli.micraft.game.keybinding.loadKeyBindings
import org.micoli.micraft.game.loadServerConfig
import org.micoli.micraft.resourcesConfigDir

val I18N_YAML_BOOTSTRAP = named("i18nYamlBootstrap")
val KEY_BINDINGS_BOOTSTRAP = named("keyBindingsBootstrap")

val configModule = module {
    single(I18N_YAML_BOOTSTRAP, createdAtStart = true) { validateAlli18nYamlConfigs(configDir) }

    single<ServerConfig> {
        loadServerConfig(
                Path.of("$dataPath/config/server.yaml"), resourcesConfigDir.resolve("server.yaml"))
            .also { applyServerConfig(it) }
    }

    single<GameConfig> { get<ServerConfig>().game }

    single(KEY_BINDINGS_BOOTSTRAP, createdAtStart = true) {
        loadKeyBindings(Path.of("$dataPath/config/keybindings.yaml"))
    }
}
