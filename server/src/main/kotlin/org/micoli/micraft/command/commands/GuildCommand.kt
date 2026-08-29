package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class GuildCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b1d3a1f0-0002-4a00-9000-000000000002")
    override val name = "guild"
    override val description = "Manage your guild."
    override val usage =
        "$command create <name> <tag>|invite <player>|accept|leave|kick <player>|motd <text>|rank <player> <rankName>|transfer <player>|disband|info"
    override val options =
        listOf(
            "create",
            "invite",
            "accept",
            "decline",
            "leave",
            "kick",
            "motd",
            "rank",
            "transfer",
            "disband",
            "info")

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
        val gm = context.guildManager ?: return
        val lang = session.state.language
        val trimmed = args.trim()
        val sub = trimmed.substringBefore(' ').lowercase()
        val rest = trimmed.substringAfter(' ', "").trim()
        when (sub) {
            "create" -> {
                val toks = rest.split(Regex("\\s+"))
                if (toks.size < 2) return usage(session, context)
                gm.create(session, toks.dropLast(1).joinToString(" "), toks.last())
            }
            "invite" -> gm.invite(session, rest)
            "accept" ->
                gm.pendingGuildIdFor(session.id)?.let { gm.respondInvite(session, it, true) }
            "decline" ->
                gm.pendingGuildIdFor(session.id)?.let { gm.respondInvite(session, it, false) }
            "leave" -> gm.leave(session)
            "kick" -> memberId(context, session, rest)?.let { gm.kick(session, it) }
            "motd" -> gm.setMotd(session, rest)
            "rank" -> {
                val toks = rest.split(Regex("\\s+"), limit = 2)
                if (toks.size < 2) return usage(session, context)
                memberId(context, session, toks[0])?.let { gm.setRank(session, it, toks[1]) }
            }
            "transfer" -> memberId(context, session, rest)?.let { gm.transferOwner(session, it) }
            "disband" -> gm.disband(session)
            "info" -> gm.sendSync(session)
            else -> usage(session, context)
        }
    }

    private suspend fun usage(session: PlayerSession, context: CommandContext) =
        session.send(
            ServerMessage.Notification(
                context.i18n.t(session.state.language, "guild:server:usage"), "system"))

    /**
     * Resolves a name to a player-id, preferring an online session, then the actor's guild roster.
     */
    private fun memberId(context: CommandContext, actor: PlayerSession, name: String): String? {
        context
            .sessions()
            .find { it.state.name.equals(name, ignoreCase = true) }
            ?.let {
                return it.id
            }
        return context.guildRegistry
            ?.guildOf(actor.id)
            ?.members
            ?.find { it.playerName.equals(name, ignoreCase = true) }
            ?.playerId
    }
}
