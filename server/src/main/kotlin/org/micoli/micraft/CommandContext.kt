package org.micoli.micraft

import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.WorldPersistence
import org.micoli.micraft.world.WorldState

data class CommandContext(
    val world: WorldState,
    val persistence: WorldPersistence?,
    val broadcast: suspend (ServerMessage) -> Unit = {},
    val sessions: () -> Collection<PlayerSession> = { emptyList() },
    val kickSession: suspend (String) -> Unit = {},
    val reloadConfig: (suspend () -> String)? = null,
    val commands: () -> Collection<CommandHandler> = { emptyList() },
)
