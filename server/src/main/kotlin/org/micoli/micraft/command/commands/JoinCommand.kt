package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class JoinCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c09ab770-6432-4caa-a672-51f3aa51f6d3")
    override val name = "join"
    override val description = "Join a chat channel."
    override val usage = "$command <channelName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<String> =
        context.chatChannelManager!!.listKnownChannels().filter {
            it.contains(partial, ignoreCase = true)
        }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val channel = args.trim()
        if (channel.isBlank()) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "join:server:usage"), "system"))
            return
        }
        if (!(context.chatChannelManager!!.channelExists(channel))) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "join:server:not_found", channel), "system"))
            return
        }
        val joined = context.chatService!!.subscribe(session, channel)
        if (!joined) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "join:server:already_member", channel),
                    "system"))
            return
        }
        context.chatService.syncChannels(session)
        session.send(
            ServerMessage.Notification(
                i18n.t(session.state.language, "join:server:joined", channel), "system"))
    }
}
