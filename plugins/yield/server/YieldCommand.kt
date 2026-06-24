package org.micoli.micraft.plugins.yield

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class YieldCommand : CommandHandler {
    override val id = UUID.fromString("5594fade-5065-4598-a66d-9b5c228e9e56")
    override val command = "/yield"
    override val description = "Broadcasts a message to all connected players."
    override val usage = "/yield <message>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        if (args.isBlank()) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(session.state.language, "yield:server:usage")))
            return
        }
        context.broadcast(ServerMessage.Notification("[${session.state.name}] $args"))
    }
}
