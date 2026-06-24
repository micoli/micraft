package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import java.util.UUID

class LayoutCommand : CommandHandler {
    override val id = UUID.fromString("5adafecc-76ca-44dd-9e06-8d492ec28bce")
    override val command = "/layout"
    override val description = "Switches to a named layout."
    override val usage = "/layout <name>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val name = args.trim()
        if (name.isBlank()) {
            session.send(ServerMessage.Notification(context.i18n.t(session.state.language, "layout:server:usage")))
            return
        }
        if (session.state.layouts.none { it.name == name }) {
            session.send(ServerMessage.Notification(context.i18n.t(session.state.language, "layout:server:not_found", name)))
            return
        }
        session.state = session.state.copy(activeLayout = name)
        context.savePlayer(session)
        session.send(ServerMessage.LayoutsSync(session.state.layouts, name))
        session.send(ServerMessage.Notification(context.i18n.t(session.state.language, "layout:server:switched", name)))
    }
}
