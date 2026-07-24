package org.micoli.micraft.plugins.kick

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(KickCommand::class.java)

class KickCommand : PluginCommand {
    override val id: UUID = UUID.fromString("13660f30-90bd-46a2-91b0-e2091813128c")
    override val name = "kick"
    override val command = "/kick"
    override val description = "Kicks a connected player."
    override val usage = "/kick <playerName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<String> =
        context
            .sessions()
            .map { it.state.name }
            .filter { it.contains(partial, ignoreCase = true) }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val target = args.trim()
        val i18n = context.i18n
        if (target.isBlank()) {
            session.send(
                ServerMessage.Notification(i18n.t(session.state.language, "kick:server:usage")))
            return
        }
        val targetSession: PlayerSession? = context.sessions().find { it.state.name == target }
        if (targetSession == null) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "kick:server:not_found", target)))
            return
        }
        targetSession.send(
            ServerMessage.Notification(
                i18n.t(targetSession.state.language, "kick:server:kicked_you", session.state.name)))
        context.kickSession(target)
        context
            .sessions()
            .filter { it.state.name != target }
            .forEach { s ->
                s.send(
                    ServerMessage.Notification(
                        i18n.t(
                            s.state.language,
                            "kick:server:kicked_broadcast",
                            session.state.name,
                            target)))
            }
        log.info("{} kicked {}", session.state.name, target)
    }
}
