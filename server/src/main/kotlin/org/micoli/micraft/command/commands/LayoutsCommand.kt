package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class LayoutsCommand : CommandHandler {
    override val id: UUID = UUID.fromString("82879132-3a02-44d3-8f27-79f0f01ca855")
    override val name = "layouts"
    override val description = "Opens the layout editor."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenLayoutEditor)
    }
}
