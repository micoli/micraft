package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class LightOffCommand : CommandHandler {
    override val id: UUID = UUID.fromString("96ba734f-bb38-497d-85ca-c0cbb5473404")
    override val name = "light:off"
    override val description = "Restores natural cavern darkness."
    override val usage = command

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.LightBoostUpdate(false))
        session.send(
            ServerMessage.Notification(context.i18n.t(session.state.language, "light:server:off")))
    }
}
