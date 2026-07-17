package org.micoli.micraft.plugin

import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.di.SessionRegistry
import org.micoli.micraft.game.world.WorldState

data class TickContext(
    val gameTicks: Long,
    val sessionRegistry: SessionRegistry,
    val world: WorldState,
    val commandContext: CommandContext,
)
