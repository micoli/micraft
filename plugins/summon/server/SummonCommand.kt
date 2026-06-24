package org.micoli.micraft.plugins.summon

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.plugins.teleport.safeTeleportPos
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class SummonCommand : CommandHandler {
    override val id = UUID.fromString("57e22c48-1f2f-486e-abca-aa8c711f8edf")
    override val command = "/summon"
    override val description = "Teleports another player to your location."
    override val usage = "/summon <playerName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<String> =
        context
            .sessions()
            .map { it.state.name }
            .filter { it.startsWith(partial, ignoreCase = true) }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val target = args.trim()
        if (target.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "summon:server:usage")))
            return
        }
        val targetSession: PlayerSession? = context.sessions().find { it.state.name == target }
        if (targetSession == null) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "summon:server:not_found", target)))
            return
        }
        targetSession.state =
            targetSession.state.copy(pos = safeTeleportPos(context.world, session.state.pos))
        targetSession.vy = 0f
        targetSession.send(ServerMessage.PlayerUpdate(targetSession.state))
        targetSession.send(
            ServerMessage.Notification(
                i18n.t(
                    targetSession.state.language,
                    "summon:server:summoned_you",
                    session.state.name)))
        session.send(ServerMessage.Notification(i18n.t(lang, "summon:server:done", target)))
    }
}
