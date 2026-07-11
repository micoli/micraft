package org.micoli.micraft.command

import org.micoli.micraft.I18nConfig
import org.micoli.micraft.auth.AuthProvider
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.config.ConfigRegistry
import org.micoli.micraft.game.armor.ArmorDefinition
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.chat.ChatService
import org.micoli.micraft.game.npc.NpcManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.trade.TradeManager
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldPersistence
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.game.world.weather.WeatherManager
import org.micoli.micraft.protocol.ServerMessage

data class CommandContext(
    val world: WorldState,
    val persistence: WorldPersistence?,
    val i18n: I18nConfig,
    val broadcast: suspend (ServerMessage) -> Unit = {},
    val sessions: () -> Collection<PlayerSession> = { emptyList() },
    val kickSession: suspend (String) -> Unit = {},
    val reloadConfig: (suspend (lang: String) -> String)? = null,
    val commands: () -> Collection<CommandHandler> = { emptyList() },
    val savePlayer: (PlayerSession) -> Unit = {},
    val worldItems: WorldItemManager? = null,
    val npcManager: NpcManager? = null,
    val getGameTime: () -> Long = { 0L },
    val setGameTime: (Long) -> Unit = {},
    val refetchChunks: (suspend (PlayerSession) -> Unit)? = null,
    val flushWorld: (() -> Unit)? = null,
    val chatService: ChatService? = null,
    val chatChannelManager: ChatChannelManager? = null,
    val weatherManager: WeatherManager? = null,
    val authProvider: AuthProvider? = null,
    val groupsConfig: GroupsConfig? = null,
    val liquidManager: LiquidManager? = null,
    val configRegistry: ConfigRegistry? = null,
    val reloadBlocks: (suspend () -> Unit)? = null,
    val reloadNpcs: (suspend () -> Unit)? = null,
    val reloadRbac: (() -> Unit)? = null,
    val armorRegistry: () -> Map<String, ArmorDefinition> = { emptyMap() },
    val tradeManager: TradeManager? = null,
    val clearTokenAccumulator: ((String) -> Unit)? = null,
    val sendStatusUpdate: (suspend (PlayerSession) -> Unit)? = null,
)
