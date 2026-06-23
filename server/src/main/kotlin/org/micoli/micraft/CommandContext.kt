package org.micoli.micraft

import org.micoli.micraft.npc.NpcManager
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.I18nConfig
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
    val reloadConfig: (suspend () -> String)? = null,
    val commands: () -> Collection<CommandHandler> = { emptyList() },
    val savePlayer: (PlayerSession) -> Unit = {},
    val worldItems: WorldItemManager? = null,
    val npcManager: NpcManager? = null,
    val getGameTime: () -> Long = { 0L },
    val setGameTime: (Long) -> Unit = {},
    val refetchChunks: (suspend (PlayerSession) -> Unit)? = null,
)
