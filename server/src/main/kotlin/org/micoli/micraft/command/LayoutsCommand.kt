package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class LayoutsCommand : CommandHandler {
    override val command = "/layouts"
    override val description = "Opens the layout editor."
    override val usage = "/layouts"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenLayoutEditor)
    }
}
