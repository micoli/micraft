package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class LightOnCommand : CommandHandler {
    override val id: UUID = UUID.fromString("906f7d30-e390-4754-9f6d-bca2a7f305d5")
    override val name = "light:on"
    override val description = "Boosts ambient light underground (cavern lighting override)."
    override val usage = command

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.LightBoostUpdate(true))
        session.send(
            ServerMessage.Notification(context.i18n.t(session.state.language, "light:server:on")))
    }
}
