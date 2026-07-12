package org.micoli.micraft.di

import org.koin.dsl.module
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.proceduralGenerator.ProceduralChunkGenerator
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.protocol.ServerMessage

/**
 * [CommandContext] fields that close over GameLoop's own per-connection/per-instance mutable state
 * (sessions, game ticks, reload closures owned by Application.module()). These cannot be resolved
 * from Koin the way the rest of the field set can, so GameLoop passes them in explicitly.
 */
data class CommandContextClosures(
    val broadcast: suspend (ServerMessage) -> Unit,
    val sessions: () -> Collection<PlayerSession>,
    val kickSession: suspend (String) -> Unit,
    val reloadConfig: (suspend (lang: String) -> String)?,
    val commands: () -> Collection<CommandHandler>,
    val savePlayer: (PlayerSession) -> Unit,
    val getGameTime: () -> Long,
    val setGameTime: (Long) -> Unit,
    val refetchChunks: (suspend (PlayerSession) -> Unit)?,
    val flushWorld: (() -> Unit)?,
    val reloadBlocks: (suspend () -> Unit)?,
    val reloadNpcs: (suspend () -> Unit)?,
    val reloadRbac: (() -> Unit)?,
    val armorRegistry: () -> Map<String, ArmorDefinition>,
)

val commandContextModule = module {
    single { (closures: CommandContextClosures) ->
        val world = get<WorldState>()
        val cavernPoints =
            (world.generator as? ProceduralChunkGenerator)?.namedCavernPoints() ?: emptyMap()
        CommandContext(
            world = world,
            persistence = get<OptionalWorldPersistence>().value,
            i18n = get<I18nConfig>(),
            broadcast = closures.broadcast,
            sessions = closures.sessions,
            kickSession = closures.kickSession,
            reloadConfig = closures.reloadConfig,
            commands = closures.commands,
            savePlayer = closures.savePlayer,
            worldItems = get<WorldItemManager>(),
            npcManager = get<NpcManager>(),
            getGameTime = closures.getGameTime,
            setGameTime = closures.setGameTime,
            refetchChunks = closures.refetchChunks,
            flushWorld = closures.flushWorld,
            chatService = get<ChatService>(),
            chatChannelManager = get<ChatChannelManager>(),
            weatherManager = get<WeatherManager>(),
            authProvider = get<OptionalAuthProvider>().value,
            groupsConfig = get<GroupsConfig>(),
            liquidManager = get<LiquidManager>(),
            configRegistry = get<ConfigRegistry>(),
            reloadBlocks = closures.reloadBlocks,
            reloadNpcs = closures.reloadNpcs,
            reloadRbac = closures.reloadRbac,
            armorRegistry = closures.armorRegistry,
            tradeManager = get<TradeManager>(),
            namedPoints = { cavernPoints },
        )
    }
}
