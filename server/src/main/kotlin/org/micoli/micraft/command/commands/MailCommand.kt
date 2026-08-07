package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class MailCommand : CommandHandler {
    override val id: UUID = UUID.fromString("e3a7c2d1-4f8b-4a9e-b0c5-1d2e3f4a5b6c")
    override val name = "mail"
    override val description = "Open your mailbox."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenMailbox)
    }
}
