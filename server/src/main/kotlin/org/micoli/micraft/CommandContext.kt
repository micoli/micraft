package org.micoli.micraft

import org.micoli.micraft.world.WorldPersistence
import org.micoli.micraft.world.WorldState

data class CommandContext(
    val world: WorldState,
    val persistence: WorldPersistence?,
)
