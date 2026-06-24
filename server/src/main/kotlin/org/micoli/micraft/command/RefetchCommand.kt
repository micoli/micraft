package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class RefetchCommand : CommandHandler {
    override val id = UUID.fromString("fb0f42fe-5bba-4318-b49e-1272534eceae")
    override val command = "/refetch"
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
