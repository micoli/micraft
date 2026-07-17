package org.micoli.micraft.di

import org.koin.core.annotation.Module

@Module(
    includes =
        [
            ConfigModule::class,
            RegistryModule::class,
            WorldModule::class,
            AuthModule::class,
            GameLoopModule::class,
            CommandContextModule::class,
        ])
class AppModule
