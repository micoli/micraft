package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.canonicalPlayerName
import org.micoli.micraft.command.playerCompletions
import org.micoli.micraft.command.resolvePlayerSession
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class GroupCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b1d3a1f0-0001-4a00-9000-000000000001")
    override val name = "group"
    override val description = "Manage your temporary party (max 5)."
    override val usage =
        "$command create|invite <player>|accept|leave|kick <player>|transfer <player>|disband|who"
    override val options =
        listOf("create", "invite", "accept", "leave", "kick", "transfer", "disband", "who")
    override val autocompleteArgs = listOf(0, 1)

    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<Completion>? {
        if (argIndex == 0) return null
        return context.playerCompletions(partial, excludeSelf = session)
    }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val gm = context.groupManager ?: return
        val parts = args.trim().split(Regex("\\s+"), limit = 2)
        val sub = parts.getOrNull(0)?.lowercase().orEmpty()
        val rest = parts.getOrNull(1)?.trim().orEmpty()
        when (sub) {
            "create" -> gm.create(session)
            "invite" -> gm.invite(session, context.canonicalPlayerName(rest))
            "accept" ->
                gm.pendingGroupIdFor(session.id)?.let { gm.respondInvite(session, it, true) }
            "decline" ->
                gm.pendingGroupIdFor(session.id)?.let { gm.respondInvite(session, it, false) }
            "leave" -> gm.leave(session)
            "kick" -> resolveId(context, rest)?.let { gm.kick(session, it) }
            "transfer" -> resolveId(context, rest)?.let { gm.transfer(session, it) }
            "disband" -> gm.disband(session)
            "who" -> gm.sendSync(session)
            else ->
                session.send(
                    ServerMessage.Notification(
                        context.i18n.t(session.state.language, "group:server:usage"), "system"))
        }
    }

    private fun resolveId(context: CommandContext, token: String): String? =
        context.resolvePlayerSession(token)?.id
}
