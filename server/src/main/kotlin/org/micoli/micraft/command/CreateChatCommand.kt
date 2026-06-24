package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.world.ChatChannelManager
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class CreateChatCommand : CommandHandler {
    override val command = "/createchat"
    override val description = "Create a new chat channel."
    override val usage = "/createChat <channelName>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val channel = args.trim()
        if (channel.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "createChat:server:usage"), "system"))
            return
        }
        if (channel in ChatChannelManager.BUILTIN || channel.startsWith("dm:")) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "createChat:server:reserved", channel), "system"))
            return
        }
        val manager = context.chatChannelManager!!
        if (manager.channelExists(channel)) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "createChat:server:exists", channel), "system"))
            return
        }
        manager.registerChannel(channel)
        context.chatService!!.subscribe(session, channel)
        context.chatService.syncChannels(session)
        session.send(ServerMessage.Notification(i18n.t(session.state.language, "createChat:server:created", channel), "system"))
    }
}
