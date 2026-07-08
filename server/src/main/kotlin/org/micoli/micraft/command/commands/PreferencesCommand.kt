package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class PreferencesCommand : CommandHandler {
    override val id: UUID = UUID.fromString("e3c4f5a6-b7d8-4e9f-a0b1-c2d3e4f5a6b7")
    override val name = "preferences"
    override val description = "Opens the preferences panel."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenPreferences)
    }
}
