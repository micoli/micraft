package org.micoli.micraft.command

import java.util.UUID
import kotlinx.serialization.json.Json
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.ItemType

class TradeOfferCommand : CommandHandler {
    override val id: UUID = UUID.fromString("bc5d4e7f-6e0a-7b1c-d5e8-f9a0b1c2d3e4")
    override val name = "tradeoffer"
    override val description = "Updates your current trade offer."
    override val usage = "$command <tradeId> <json>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val tradeManager = context.tradeManager ?: return
        val tradeId = args.substringBefore(' ').trim()
        val jsonPart = args.substringAfter(' ', "").trim()
        if (tradeId.isBlank() || jsonPart.isBlank()) return
        val offer =
            runCatching {
                    Json.decodeFromString<Map<String, Int>>(jsonPart).mapKeys { (k, _) ->
                        ItemType(k)
                    }
                }
                .getOrNull() ?: return
        tradeManager.updateOffer(session, tradeId, offer)
    }
}
