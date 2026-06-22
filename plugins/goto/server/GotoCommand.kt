package org.micoli.micraft.plugins.goto

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.plugins.teleport.safeTeleportPos
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class GotoCommand : CommandHandler {
    override val command = "/goto"
    override val description = "Teleports you to another player's location."
    override val usage = "/goto <playerName>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val target = args.trim()
        if (target.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "goto:server:usage")))
            return
        }
        val targetSession: PlayerSession? = context.sessions().find { it.state.name == target }
        if (targetSession == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "goto:server:not_found", target)))
            return
        }
        session.state = session.state.copy(pos = safeTeleportPos(context.world, targetSession.state.pos))
        session.vy = 0f
        session.send(ServerMessage.PlayerUpdate(session.state))
        session.send(ServerMessage.Notification(i18n.t(lang, "goto:server:done", target)))
    }
}
