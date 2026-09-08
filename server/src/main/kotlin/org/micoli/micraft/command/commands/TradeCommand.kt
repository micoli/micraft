package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.canonicalPlayerName
import org.micoli.micraft.command.playerCompletions
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class TradeCommand : CommandHandler {
    override val id: UUID = UUID.fromString("8f2a1c4e-3b7d-4e9f-a2b5-c6d8e0f1a2b3")
    override val name = "trade"
    override val description = "Initiates a trade with another player."
    override val usage = "$command <playerName>"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<Completion> = context.playerCompletions(partial, excludeSelf = session)

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val tradeManager = context.tradeManager
        if (tradeManager == null) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(session.state.language, "trade:server:usage")))
            return
        }
        tradeManager.initiate(session, context.canonicalPlayerName(args.trim()))
    }
}
