package org.micoli.micraft.plugins.summon

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.command.playerCompletions
import org.micoli.micraft.command.resolvePlayerSession
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.plugins.teleport.safeTeleportPos
import org.micoli.micraft.protocol.ServerMessage

class SummonCommand : PluginCommand {
    override val id: UUID = UUID.fromString("9400ab9b-8643-4b0f-9cbe-a49d3ce59ab0")
    override val name = "summon"
    override val command = "/summon"
    override val description = "Teleports another player to your location."
    override val usage = "/summon <playerName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<Completion> = context.playerCompletions(partial)

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val token = args.trim()
        if (token.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "summon:server:usage")))
            return
        }
        val targetSession: PlayerSession? = context.resolvePlayerSession(token)
        val target = targetSession?.state?.name ?: token
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
