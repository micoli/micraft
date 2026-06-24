package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import java.util.UUID

class TalkCommand : CommandHandler {
    override val id = UUID.fromString("f9ab78d3-d0da-4b6d-a418-2823ce4e47fa")
    override val command = "/talk"
    override val description = "Open a private chat with a player."
    override val usage = "/talk <playerName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(argIndex: Int, partial: String, session: PlayerSession?, context: CommandContext): List<String> =
        context.sessions().map { it.state.name }.filter { it.startsWith(partial, ignoreCase = true) }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val targetName = args.trim()
        if (targetName.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "talk:server:usage"), "system"))
            return
        }
        val target = context.sessions().find { it.state.name == targetName }
        if (target == null) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "talk:server:not_found", targetName), "system"))
            return
        }
        context.chatService!!.openDm(session, target)
        session.send(ServerMessage.Notification(i18n.t(session.state.language, "talk:server:opened", targetName), "system"))
    }
}
