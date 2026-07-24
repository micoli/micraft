package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class TalkCommand : CommandHandler {
    override val id: UUID = UUID.fromString("f9ab78d3-d0da-4b6d-a418-2823ce4e47fa")
    override val name = "talk"
    override val description = "Open a private chat with a player."
    override val usage = "$command <playerName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<String> =
        context.sessions().map { it.state.name }.filter { it.contains(partial, ignoreCase = true) }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val targetName = args.trim()
        if (targetName.isBlank()) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "talk:server:usage"), "system"))
            return
        }
        val target = context.sessions().find { it.state.name == targetName }
        if (target == null) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "talk:server:not_found", targetName), "system"))
            return
        }
        context.chatService!!.openDm(session, target)
        session.send(
            ServerMessage.Notification(
                i18n.t(session.state.language, "talk:server:opened", targetName), "system"))
    }
}
