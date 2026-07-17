package org.micoli.micraft.plugin

import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.ServerMessage

data class TickContext(
    val gameTicks: Long,
    val sessionRegistry: SessionRegistry,
    val world: WorldState,
    val commandContext: CommandContext,
)
