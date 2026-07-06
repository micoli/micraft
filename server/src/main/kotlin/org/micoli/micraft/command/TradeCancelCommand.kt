package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.session.PlayerSession

class TradeCancelCommand : CommandHandler {
    override val id: UUID = UUID.fromString("ab4c3d6e-5d9f-6a0b-c4d7-e8f0a1b2c3d4")
    override val name = "tradecancel"
    override val description = "Cancels the current trade."
    override val usage = "$command <tradeId>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        context.tradeManager?.cancel(session, args.trim())
    }
}
