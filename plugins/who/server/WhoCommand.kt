package org.micoli.micraft.plugins.who

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class WhoCommand : CommandHandler {
    override val id = UUID.fromString("015f4e2a-2a74-4d2e-9692-e883f2b8bdf2")
    override val command = "/who"
    override val description = "Lists connected players with their position."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val connected: List<PlayerSession> = context.sessions().toList()
        if (connected.isEmpty()) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "who:server:empty")))
            return
        }
        val lines =
            connected.joinToString("\n") { s ->
                val p = s.state.pos
                val suffix = if (s.userName != s.state.name) " (user: ${s.userName})" else ""
                "  ${s.state.name}$suffix at (${p.x.toInt()}, ${p.y.toInt()}, ${p.z.toInt()})"
            }
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "who:server:online", connected.size, lines)))
    }
}
