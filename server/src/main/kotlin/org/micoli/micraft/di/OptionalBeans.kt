package org.micoli.micraft.di

import org.micoli.micraft.auth.AuthProvider
import org.micoli.micraft.auth.NoAuthAccountStore
import org.micoli.micraft.auth.TokenStore
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.house.HouseConfig
import org.micoli.micraft.game.world.road.RoadConfig

/**
 * Koin's `single {}` cannot hold a `null` value (the instance factory throws once created), so
 * beans that are legitimately absent (debug world, auth disabled) are wrapped in one of these
 * non-null holders instead of registered as `single<T?>`.
 */
class OptionalWorldPersistence(val value: WorldPersistence?)

class OptionalRoadConfig(val value: RoadConfig?)

class OptionalHouseConfig(val value: HouseConfig?)

class OptionalAuthProvider(val value: AuthProvider?)

class OptionalTokenStore(val value: TokenStore?)

class OptionalNoAuthAccountStore(val value: NoAuthAccountStore?)

class I18nBootstrapResult

class KeyBindingsBootstrapResult

class RegistryBootstrapResult
