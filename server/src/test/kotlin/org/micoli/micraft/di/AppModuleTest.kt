package org.micoli.micraft.di

import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.ksp.generated.module
import org.koin.test.check.checkModules
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.social.FactionManager
import org.micoli.micraft.game.social.GroupManager
import org.micoli.micraft.game.social.GuildManager
import org.micoli.micraft.game.social.GuildRegistry
import org.micoli.micraft.support.testI18n

class AppModuleTest {
    @Suppress("DEPRECATION")
    @Test
    fun `all Koin module definitions resolve`() {
        koinApplication { modules(AppModule().module) }
            .checkModules {
                withParameter<CommandContext> {
                    val cm = ChatChannelManager()
                    val chat = ChatService(cm, {}, { emptyList() })
                    val i18n = testI18n()
                    val guildReg = GuildRegistry(null)
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
                        groupManager = GroupManager({ emptyList() }, chat, cm, i18n),
                        guildManager = GuildManager(guildReg, { emptyList() }, {}, chat, cm, i18n),
                        guildRegistry = guildReg,
                        factionManager = FactionManager({ emptyList() }, {}, chat, cm, i18n, {}),
                    )
                }
            }
    }
}
