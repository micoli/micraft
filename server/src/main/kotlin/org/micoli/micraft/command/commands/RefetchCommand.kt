package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class RefetchCommand : CommandHandler {
    override val id: UUID = UUID.fromString("fb0f42fe-5bba-4318-b49e-1272534eceae")
    override val name = "refetch"
    override val description = "Reloads all chunks around the player."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val refetch = context.refetchChunks
        if (refetch == null) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(session.state.language, "refetch:server:unavailable")))
            return
        }
        refetch(session)
        session.send(
            ServerMessage.Notification(
                context.i18n.t(session.state.language, "refetch:server:done")))
    }
}
