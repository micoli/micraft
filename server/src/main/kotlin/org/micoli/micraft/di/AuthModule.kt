package org.micoli.micraft.di

import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.auth.LocalAuthProvider
import org.micoli.micraft.auth.NoAuthAccountStore
import org.micoli.micraft.auth.OAuthProvider
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.auth.loadGroupsConfig
import org.micoli.micraft.config.ConfigPaths
import org.micoli.micraft.game.ServerConfig

@Module
class AuthModule {
    @Single fun coroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.Default)

    @Single
    fun groupsConfig(serverConfig: ServerConfig): GroupsConfig {
        val authConfig = serverConfig.auth
        return loadGroupsConfig(
            Path.of(authConfig.local.groupsFile), ConfigPaths.resourcesConfig("groups.yaml"))
    }

    @Single
    fun optionalAuthProvider(
        serverConfig: ServerConfig,
        groupsConfig: GroupsConfig,
    ): OptionalAuthProvider {
        val authConfig = serverConfig.auth
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
        return OptionalAuthProvider(provider)
    }

    @Single
    fun optionalTokenStore(
        coroutineScope: CoroutineScope,
        optionalAuthProvider: OptionalAuthProvider,
    ): OptionalTokenStore =
        OptionalTokenStore(
            if (optionalAuthProvider.value != null) TokenStore(coroutineScope) else null)

    @Single
    fun optionalNoAuthAccountStore(serverConfig: ServerConfig): OptionalNoAuthAccountStore {
        val authConfig = serverConfig.auth
        return OptionalNoAuthAccountStore(
            if (authConfig.provider == "none")
                NoAuthAccountStore(ConfigPaths.dataConfig("auth/noauth_accounts.yaml"))
            else null)
    }
}
