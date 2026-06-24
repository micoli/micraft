package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import java.util.UUID

class LayoutsCommand : CommandHandler {
    override val id = UUID.fromString("82879132-3a02-44d3-8f27-79f0f01ca855")
    override val command = "/layouts"
    override val description = "Opens the layout editor."
    override val usage = "/layouts"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenLayoutEditor)
    }
}
