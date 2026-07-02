package org.micoli.micraft

import org.micoli.micraft.auth.AuthProvider
import org.micoli.micraft.auth.GroupsConfig
import org.micoli.micraft.npc.NpcManager
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.tick.LiquidManager
import org.micoli.micraft.world.ChatChannelManager
import org.micoli.micraft.world.ChatService
import org.micoli.micraft.world.I18nConfig
import org.micoli.micraft.world.WearableSlots
import org.micoli.micraft.world.WeatherManager
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldPersistence
import org.micoli.micraft.world.WorldState

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
    val armorRegistry: () -> Map<String, WearableSlots> = { emptyMap() },
)
