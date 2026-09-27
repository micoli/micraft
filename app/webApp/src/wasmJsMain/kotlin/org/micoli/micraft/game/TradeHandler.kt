package org.micoli.micraft.game

import kotlinx.serialization.json.Json
import org.micoli.micraft.babylon.jsOpenTrade
import org.micoli.micraft.babylon.jsTradeClosed
import org.micoli.micraft.babylon.jsTradeUpdate
import org.micoli.micraft.protocol.ServerMessage

/** Player-to-player trade session lifecycle. */
class TradeHandler : ServerMessageHandler {
    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.OpenTrade -> jsOpenTrade(msg.tradeId, msg.otherPlayerName, msg.myRole)
            is ServerMessage.TradeUpdate -> jsTradeUpdate(Json.encodeToString(msg))
            is ServerMessage.TradeClosed -> jsTradeClosed(msg.tradeId, msg.reason)
            else -> Unit
        }
}
