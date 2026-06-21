package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class YieldCommand : CommandHandler {
    override val command = "/yield"
    override val description = "Diffuse un message à tous les joueurs connectés."
    override val usage = "/yield <message>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        if (args.isBlank()) {
            session.send(ServerMessage.Notification("Usage: /yield <message>"))
            return
        }
        context.broadcast(ServerMessage.Notification("[${session.state.name}] $args"))
    }
}
