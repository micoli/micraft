package org.micoli.micraft.game.trade

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator
import kotlin.math.sqrt
import org.micoli.micraft.I18nConfig
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ServerMessage

class TradeManager(
    private val getSessions: () -> Collection<PlayerSession>,
    private val i18n: I18nConfig,
    private val savePlayer: (PlayerSession) -> Unit,
    private val maxDistance: Float = 10f,
) {
    private val trades = ConcurrentHashMap<String, PendingTrade>()

    suspend fun initiate(initiator: PlayerSession, targetName: String) {
        val lang = initiator.state.language
        if (targetName.isBlank() || targetName.equals(initiator.state.name, ignoreCase = true)) {
            initiator.send(ServerMessage.Notification(i18n.t(lang, "trade:server:usage")))
            return
        }
        val target = getSessions().find { it.state.name.equals(targetName, ignoreCase = true) }
        if (target == null) {
            initiator.send(
                ServerMessage.Notification(i18n.t(lang, "trade:server:not_found", targetName)))
            return
        }
        if (distance(initiator, target) > maxDistance) {
            initiator.send(
                ServerMessage.Notification(
                    i18n.t(lang, "trade:server:too_far", targetName, maxDistance.toInt())))
            return
        }
        if (trades.containsKey(initiator.id) || trades.containsKey(target.id)) {
            initiator.send(
                ServerMessage.Notification(
                    i18n.t(lang, "trade:server:already_trading", targetName)))
            return
        }
        val trade =
            PendingTrade(
                id = UUID.randomUUID().toString(),
                initiatorId = initiator.id,
                targetId = target.id,
            )
        trades[initiator.id] = trade
        trades[target.id] = trade
        initiator.send(ServerMessage.OpenTrade(trade.id, target.state.name, "initiator"))
        target.send(ServerMessage.OpenTrade(trade.id, initiator.state.name, "target"))
    }

    suspend fun updateOffer(session: PlayerSession, tradeId: String, offer: Map<ItemType, Int>) {
        val trade = trades[session.id]?.takeIf { it.id == tradeId } ?: return
        val offerMap =
            if (session.id == trade.initiatorId) trade.initiatorOffer else trade.targetOffer
        offerMap.clear()
        offerMap.putAll(offer)
        trade.initiatorAccepted = false
        trade.targetAccepted = false
        broadcastTradeUpdate(trade)
    }

    suspend fun accept(session: PlayerSession, tradeId: String) {
        val trade = trades[session.id]?.takeIf { it.id == tradeId } ?: return
        if (session.id == trade.initiatorId) trade.initiatorAccepted = true
        else trade.targetAccepted = true
        if (trade.initiatorAccepted && trade.targetAccepted) {
            if (trade.closed.compareAndSet(false, true)) {
                executeTrade(trade)
            }
        } else {
            broadcastTradeUpdate(trade)
        }
    }

    suspend fun cancel(session: PlayerSession, tradeId: String, reason: String = "cancelled") {
        val trade = trades[session.id]?.takeIf { it.id == tradeId } ?: return
        if (trade.closed.compareAndSet(false, true)) {
            sendCloseNotifications(trade, reason)
        }
    }

    suspend fun onPlayerDisconnect(sessionId: String) {
        val trade = trades[sessionId] ?: return
        if (trade.closed.compareAndSet(false, true)) {
            sendCloseNotifications(trade, "disconnected")
        }
    }

    private suspend fun executeTrade(trade: PendingTrade) {
        val initiator = getSessions().find { it.id == trade.initiatorId }
        val target = getSessions().find { it.id == trade.targetId }
        if (initiator == null || target == null) {
            sendCloseNotifications(trade, "disconnected")
            return
        }
        for ((type, count) in trade.initiatorOffer) {
            if ((initiator.inventory[type] ?: 0) < count) {
                sendCloseNotifications(trade, "inventory_changed")
                return
            }
        }
        for ((type, count) in trade.targetOffer) {
            if ((target.inventory[type] ?: 0) < count) {
                sendCloseNotifications(trade, "inventory_changed")
                return
            }
        }
        for ((type, count) in trade.initiatorOffer) {
            val remaining = (initiator.inventory[type] ?: 0) - count
            if (remaining <= 0) initiator.inventory.remove(type)
            else initiator.inventory[type] = remaining
            target.inventory.merge(type, count, Int::plus)
        }
        for ((type, count) in trade.targetOffer) {
            val remaining = (target.inventory[type] ?: 0) - count
            if (remaining <= 0) target.inventory.remove(type)
            else target.inventory[type] = remaining
            initiator.inventory.merge(type, count, Int::plus)
        }
        savePlayer(initiator)
        savePlayer(target)
        initiator.send(ServerMessage.InventoryUpdate(initiator.inventory.toMap()))
        target.send(ServerMessage.InventoryUpdate(target.inventory.toMap()))
        initiator.send(
            ServerMessage.Notification(i18n.t(initiator.state.language, "trade:server:completed")))
        target.send(
            ServerMessage.Notification(i18n.t(target.state.language, "trade:server:completed")))
        removeTrade(trade)
        initiator.send(ServerMessage.TradeClosed(trade.id, "completed"))
        target.send(ServerMessage.TradeClosed(trade.id, "completed"))
    }

    private suspend fun sendCloseNotifications(trade: PendingTrade, reason: String) {
        val initiator = getSessions().find { it.id == trade.initiatorId }
        val target = getSessions().find { it.id == trade.targetId }
        val notifKey =
            when (reason) {
                "disconnected" -> "trade:server:disconnected"
                "inventory_changed" -> "trade:server:inventory_changed"
                else -> "trade:server:cancelled"
            }
        initiator?.send(ServerMessage.Notification(i18n.t(initiator.state.language, notifKey)))
        target?.send(ServerMessage.Notification(i18n.t(target.state.language, notifKey)))
        removeTrade(trade)
        val closeMsg = ServerMessage.TradeClosed(trade.id, reason)
        initiator?.send(closeMsg)
        target?.send(closeMsg)
    }

    private fun removeTrade(trade: PendingTrade) {
        trades.remove(trade.initiatorId, trade)
        trades.remove(trade.targetId, trade)
    }

    private suspend fun broadcastTradeUpdate(trade: PendingTrade) {
        val initiator = getSessions().find { it.id == trade.initiatorId } ?: return
        val target = getSessions().find { it.id == trade.targetId } ?: return
        initiator.send(
            ServerMessage.TradeUpdate(
                tradeId = trade.id,
                myOffer = trade.initiatorOffer.toMap(),
                theirOffer = trade.targetOffer.toMap(),
                myAccepted = trade.initiatorAccepted,
                theirAccepted = trade.targetAccepted,
            ))
        target.send(
            ServerMessage.TradeUpdate(
                tradeId = trade.id,
                myOffer = trade.targetOffer.toMap(),
                theirOffer = trade.initiatorOffer.toMap(),
                myAccepted = trade.targetAccepted,
                theirAccepted = trade.initiatorAccepted,
            ))
    }

    private fun distance(a: PlayerSession, b: PlayerSession): Float {
        val dx = a.state.pos.x - b.state.pos.x
        val dy = a.state.pos.y - b.state.pos.y
        val dz = a.state.pos.z - b.state.pos.z
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }
}
