package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.ChatChannelManager

class LeaveCommand : CommandHandler {
    override val id: UUID = UUID.fromString("84c30ce7-f68a-411f-b457-87ae56738241")
    override val name = "leave"
    override val description = "Leave a chat channel."
    override val usage = "$command <channelName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<String> =
        (session?.state?.subscribedChannels ?: emptyList())
            .map { it.name }
            .filter { it.startsWith(partial, ignoreCase = true) }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val channel = args.trim()
        if (channel.isBlank()) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "leave:server:usage"), "system"))
            return
        }
        if (channel in ChatChannelManager.PROTECTED) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "leave:server:cannot_leave", channel), "system"))
            return
        }
        val left = context.chatService!!.unsubscribe(session, channel)
        if (!left) {
            session.send(
                ServerMessage.Notification(
                    i18n.t(session.state.language, "leave:server:not_member", channel), "system"))
            return
        }
        context.chatService.syncChannels(session)
        session.send(
            ServerMessage.Notification(
                i18n.t(session.state.language, "leave:server:left", channel), "system"))
    }
}
