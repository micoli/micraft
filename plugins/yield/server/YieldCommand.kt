package org.micoli.micraft.plugins.yield

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class YieldCommand : PluginCommand {
    override val id: UUID = UUID.fromString("1eb67076-83cd-41da-9895-f37b0a0927a6")
    override val name = "yield"
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
