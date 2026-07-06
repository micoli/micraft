package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class TradeCommand : CommandHandler {
    override val id: UUID = UUID.fromString("8f2a1c4e-3b7d-4e9f-a2b5-c6d8e0f1a2b3")
    override val name = "trade"
    override val description = "Initiates a trade with another player."
    override val usage = "$command <playerName>"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        context
            .sessions()
            .filter { it.state.name != session?.state?.name }
            .map { it.state.name }
            .filter { it.startsWith(partial, ignoreCase = true) }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val tradeManager = context.tradeManager
        if (tradeManager == null) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(session.state.language, "trade:server:usage")))
            return
        }
        tradeManager.initiate(session, args.trim())
    }
}
