package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.PlayerState

@Serializable
data class PlayerFile(
    val state: PlayerState,
    val keybindings: Map<String, List<String>> = emptyMap(),
    val customCommands: Map<String, List<String>> = emptyMap(),
)
