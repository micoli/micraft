package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.session.PlayerSession

class TradeAcceptCommand : CommandHandler {
    override val id: UUID = UUID.fromString("9a3b2c5d-4c8e-5f0a-b3c6-d7e9f0a1b2c3")
    override val command = "/tradeaccept"
    override val description = "Accepts the current trade offer."
    override val usage = "/tradeaccept <tradeId>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        context.tradeManager?.accept(session, args.trim())
    }
}
