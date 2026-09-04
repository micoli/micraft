package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.AuctionFilter
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(AuctionCommand::class.java)

class AuctionCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d3f1a9e2-6c8b-4f7a-9d0e-1b2c3d4e5f60")
    override val name = "auction"
    override val description = "Opens the auction house."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val auctionManager = context.auctionManager
        if (auctionManager == null) {
            log.warn("/auction: auctionManager unavailable for player {}", session.state.name)
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(session.state.language, "auction:server:unavailable")))
            return
        }
        log.info("/auction: sending OpenAuctionHouse to player {}", session.state.name)
        session.send(ServerMessage.OpenAuctionHouse)
        auctionManager.setFilter(session, AuctionFilter())
    }
}
