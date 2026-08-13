package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
@JsonSchemaRoot(file = "player.schema.json")
data class PlayerFile(
    val state: PlayerState,
    val keybindings: Map<String, List<String>> = emptyMap(),
    val customCommands: Map<String, List<String>> = emptyMap(),
)
