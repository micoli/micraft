package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.chat.ChatChannelManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class CreateChatCommand : CommandHandler {
    override val id: UUID = UUID.fromString("e30d38dc-aee3-44e8-9f56-4800a1cef7ff")
    override val name = "createchat"
    override val description = "Create a new chat channel."
    override val usage = "$command <channelName>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val channel = args.trim()
        if (channel.isBlank()) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "createChat:server:usage"), "system"))
            return
        }
        if (channel in ChatChannelManager.BUILTIN || channel.startsWith("dm:")) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "createChat:server:reserved", channel),
                    "system"))
            return
        }
        val manager = context.chatChannelManager!!
        if (manager.channelExists(channel)) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "createChat:server:exists", channel), "system"))
            return
        }
        manager.registerChannel(channel)
        context.chatService!!.subscribe(session, channel)
        context.chatService.syncChannels(session)
        session.send(
            ServerMessage.Notification(
                i18n.t(session.state.language, "createChat:server:created", channel), "system"))
    }
}
