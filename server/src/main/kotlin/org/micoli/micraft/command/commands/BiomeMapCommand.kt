package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class IngameMapCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a3b4c5d6-e7f8-4901-b234-c5d6e7f8a901")
    override val name = "map"
    override val description = "Toggles the biome map overlay."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.ToggleIngameMap)
    }
}
