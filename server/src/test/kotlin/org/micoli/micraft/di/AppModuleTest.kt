package org.micoli.micraft.di

import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.ksp.generated.module
import org.koin.test.check.checkModules
import org.micoli.micraft.command.CommandContext

class AppModuleTest {
    @Suppress("DEPRECATION")
    @Test
    fun `all Koin module definitions resolve`() {
        koinApplication { modules(AppModule().module) }
            .checkModules {
                withParameter<CommandContext> {
                    CommandContextClosures(
                        broadcast = {},
                        sessions = { emptyList() },
                        kickSession = {},
                        reloadConfig = null,
                        commands = { emptyList() },
                        savePlayer = {},
                        getGameTime = { 0L },
                        setGameTime = {},
                        refetchChunks = null,
                        flushWorld = null,
                        reloadBlocks = null,
                        reloadNpcs = null,
                        reloadRbac = null,
                        armorRegistry = { emptyMap() },
                        weaponRegistry = { emptyMap() },
                        toolRegistry = { emptyMap() },
                        weaponCategories = { emptyMap() },
                        toolCategories = { emptyMap() },
                        applyBuff = { _, _, _ -> },
                    )
                }
            }
    }
}
