package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
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

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> {
        if (argIndex == 0) return options.filter { it.contains(partial, ignoreCase = true) }
        return context
            .sessions()
            .map { it.state.name }
            .filter { it.contains(partial, ignoreCase = true) }
    }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val gm = context.groupManager ?: return
        val parts = args.trim().split(Regex("\\s+"), limit = 2)
        val sub = parts.getOrNull(0)?.lowercase().orEmpty()
        val rest = parts.getOrNull(1)?.trim().orEmpty()
        when (sub) {
            "create" -> gm.create(session)
            "invite" -> gm.invite(session, rest)
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

    private fun resolveId(context: CommandContext, name: String): String? =
        context.sessions().find { it.state.name.equals(name, ignoreCase = true) }?.id
}
