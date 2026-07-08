package org.micoli.micraft.di

import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.auth.LocalAuthProvider
import org.micoli.micraft.auth.OAuthProvider
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.auth.loadGroupsConfig
import org.micoli.micraft.game.ServerConfig
import org.micoli.micraft.resourcesConfigDir

val authModule = module {
    single { CoroutineScope(Dispatchers.Default) }

    single<GroupsConfig> {
        val authConfig = get<ServerConfig>().auth
        loadGroupsConfig(
            Path.of(authConfig.local.groupsFile), resourcesConfigDir.resolve("groups.yaml"))
    }

    single {
        val authConfig = get<ServerConfig>().auth
        val groupsConfig = get<GroupsConfig>()
        val provider =
            when (authConfig.provider) {
                "local" -> LocalAuthProvider(Path.of(authConfig.local.usersFile), groupsConfig)
                "oauth" -> {
                    val oauthCfg =
                        authConfig.oauth ?: error("auth.oauth config required when provider=oauth")
                    OAuthProvider(oauthCfg, groupsConfig)
                }
                else -> null
            }
        OptionalAuthProvider(provider)
    }

    single {
        val authProvider = get<OptionalAuthProvider>().value
        OptionalTokenStore(if (authProvider != null) TokenStore(get<CoroutineScope>()) else null)
    }
}
