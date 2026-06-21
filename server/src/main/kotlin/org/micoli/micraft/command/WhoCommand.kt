package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class WhoCommand : CommandHandler {
    override val command = "/who"
    override val description = "Liste les joueurs connectés avec leur position."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val connected: List<PlayerSession> = context.sessions().toList()
        if (connected.isEmpty()) {
            session.send(ServerMessage.Notification("No players connected."))
            return
        }
        val lines = connected.joinToString("\n") { s ->
            val p = s.state.pos
            val suffix = if (s.userName != s.state.name) " (user: ${s.userName})" else ""
            "  ${s.state.name}$suffix at (${p.x.toInt()}, ${p.y.toInt()}, ${p.z.toInt()})"
        }
        session.send(ServerMessage.Notification("Online (${connected.size}):\n$lines"))
    }
}
