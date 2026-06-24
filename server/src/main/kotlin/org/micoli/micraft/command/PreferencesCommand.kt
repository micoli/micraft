package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class PreferencesCommand : CommandHandler {
    override val id: UUID = UUID.fromString("e3c4f5a6-b7d8-4e9f-a0b1-c2d3e4f5a6b7")
    override val command = "/preferences"
    override val description = "Opens the preferences panel."
    override val usage = "/preferences"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenPreferences)
    }
}
