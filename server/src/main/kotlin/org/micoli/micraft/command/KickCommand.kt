package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(KickCommand::class.java)

class KickCommand : CommandHandler {
    override val command = "/kick"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val target = args.trim()
        if (target.isBlank()) {
            session.send(ServerMessage.Notification("Usage: /kick <playerName>"))
            return
        }
        val targetSession: PlayerSession? = context.sessions().find { it.state.name == target }
        if (targetSession == null) {
            session.send(ServerMessage.Notification("Player not found: $target"))
            return
        }
        targetSession.send(ServerMessage.Notification("You have been kicked by ${session.state.name}."))
        context.kickSession(target)
        context.broadcast(ServerMessage.Notification("${session.state.name} kicked $target."))
        log.info("{} kicked {}", session.state.name, target)
    }
}
