package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class JoinCommand : CommandHandler {
    override val command = "/join"
    override val description = "Join a chat channel."
    override val usage = "/join <channelName>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val channel = args.trim()
        if (channel.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "join:server:usage"), "system"))
            return
        }
        if (!(context.chatChannelManager!!.channelExists(channel))) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "join:server:not_found", channel), "system"))
            return
        }
        val joined = context.chatService!!.subscribe(session, channel)
        if (!joined) {
            session.send(ServerMessage.Notification(i18n.t(session.state.language, "join:server:already_member", channel), "system"))
            return
        }
        context.chatService.syncChannels(session)
        session.send(ServerMessage.Notification(i18n.t(session.state.language, "join:server:joined", channel), "system"))
    }
}
