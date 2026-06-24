package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.world.ChatChannelManager
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class LeaveCommand : CommandHandler {
    override val command = "/leave"
    override val description = "Leave a chat channel."
    override val usage = "/leave <channelName>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val channel = args.trim()
        if (channel.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "leave:server:usage"), "system"))
            return
        }
        if (channel in ChatChannelManager.PROTECTED) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "leave:server:cannot_leave", channel), "system"))
            return
        }
        val left = context.chatService!!.unsubscribe(session, channel)
        if (!left) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "leave:server:not_member", channel), "system"))
            return
        }
        context.chatService.syncChannels(session)
        session.send(ServerMessage.Notification(i18n.t(session.state.language, "leave:server:left", channel), "system"))
    }
}
