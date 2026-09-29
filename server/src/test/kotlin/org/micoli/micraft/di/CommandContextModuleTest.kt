package org.micoli.micraft.di

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.koin.dsl.koinApplication
import org.koin.ksp.generated.module
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.combat.RegenProcessor
import org.micoli.micraft.game.minigame.MiniGameManager
import org.micoli.micraft.game.minigame.MiniGameRegistry
import org.micoli.micraft.game.social.FactionManager
import org.micoli.micraft.game.social.GroupManager
import org.micoli.micraft.game.social.GuildManager
import org.micoli.micraft.game.social.GuildRegistry
import org.micoli.micraft.support.testI18n

/**
 * Regression coverage for the production [CommandContext] built by Koin
 * ([CommandContextModule.commandContext], reached via `Application.kt`'s `commandContextFactory`).
 * [AppModuleTest]'s `checkModules` only verifies every dependency *resolves* — it doesn't verify
 * [CommandContextModule.commandContext] actually *wires* a resolved dependency into the
 * [CommandContext] it returns. A field silently left at its `null` default (like
 * `placeableManager`/`siegeWeaponManager` once were, breaking `/panel` and `/siege_weapon` for
 * every real player, undetected by unit tests that build [CommandContext] directly) compiles and
 * passes `checkModules` fine, so every field a command handler reads from context needs an explicit
 * non-null assertion here.
 */
class CommandContextModuleTest {
    @Test
    fun commandContext_wiresEveryManagerFieldNonNull() {
        val cm = ChatChannelManager()
        val chat = ChatService(cm, {}, { emptyList() })
        val i18n = testI18n()
        val guildReg = GuildRegistry(null)
        val miniGameReg = MiniGameRegistry()
        val closures =
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
                castProtection = { _ -> },
                groupManager = GroupManager({ emptyList() }, chat, cm, i18n),
                miniGameManager = MiniGameManager({ emptyList() }, miniGameReg, i18n),
                miniGameRegistry = miniGameReg,
                guildManager = GuildManager(guildReg, { emptyList() }, {}, chat, cm, i18n),
                guildRegistry = guildReg,
                factionManager = FactionManager({ emptyList() }, {}, chat, cm, i18n),
            )

        val koin = koinApplication { modules(AppModule().module) }.koin
        val context = koin.get<CommandContext> { org.koin.core.parameter.parametersOf(closures) }

        assertNotNull(
            context.placeableManager, "placeableManager must be wired for /panel, /siege_weapon")
        assertNotNull(
            context.siegeWeaponManager, "siegeWeaponManager must be wired for /siege_weapon")
        assertNotNull(context.vehicleManager)
        assertNotNull(context.npcManager)
        assertNotNull(context.claimRegistry)
        assertNotNull(context.claimManager)
        assertNotNull(context.actionBlockRegistry)
        assertNotNull(context.scenes)
        assertNotNull(context.clearAccumulators, "clearAccumulators must be wired for /rest")
    }

    /**
     * `/rest` (`RestCommand`) calls `context.clearAccumulators` — regression for the Koin
     * [CommandContext] leaving it at its `null` default while `GameLoop`'s own constructor default
     * wired it, masking the bug in tests built from those defaults.
     */
    @Test
    fun commandContext_clearAccumulators_reachesTheProductionRegenProcessor() {
        val cm = ChatChannelManager()
        val chat = ChatService(cm, {}, { emptyList() })
        val i18n = testI18n()
        val guildReg = GuildRegistry(null)
        val miniGameReg = MiniGameRegistry()
        val closures =
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
                castProtection = { _ -> },
                groupManager = GroupManager({ emptyList() }, chat, cm, i18n),
                miniGameManager = MiniGameManager({ emptyList() }, miniGameReg, i18n),
                miniGameRegistry = miniGameReg,
                guildManager = GuildManager(guildReg, { emptyList() }, {}, chat, cm, i18n),
                guildRegistry = guildReg,
                factionManager = FactionManager({ emptyList() }, {}, chat, cm, i18n),
            )

        val koin = koinApplication { modules(AppModule().module) }.koin
        val regenProcessor = koin.get<RegenProcessor>()
        val context = koin.get<CommandContext> { org.koin.core.parameter.parametersOf(closures) }

        // Seed a fractional regen accumulator directly on the production RegenProcessor Koin
        // manages, then prove context.clearAccumulators reaches that exact instance.
        val field = RegenProcessor::class.java.getDeclaredField("hpAccumulators")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val hpAccumulators = field.get(regenProcessor) as MutableMap<String, Float>
        hpAccumulators["sess-1"] = 0.75f

        context.clearAccumulators!!.invoke("sess-1")

        assertTrue(hpAccumulators.isEmpty(), "/rest must clear the RegenProcessor's accumulators")
    }
}
