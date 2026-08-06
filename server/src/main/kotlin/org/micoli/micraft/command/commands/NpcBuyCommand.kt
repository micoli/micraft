package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.npc.CurrencyUtils
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ServerMessage

class NpcBuyCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c1a2b3d4-e5f6-7890-abcd-ef1234567890")
    override val name = "npcbuy"
    override val description = "Buy an item from a seller NPC."
    override val usage = "$command <npcId> <itemType> [quantity]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val parts = args.trim().split(Regex("\\s+"))
        val npcId = parts.getOrNull(0).orEmpty()
        val itemTypeName = parts.getOrNull(1).orEmpty()
        val quantity = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        if (npcId.isBlank() || itemTypeName.isBlank()) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "npc:server:buy_usage")))
            return
        }

        val npcManager = context.npcManager
        if (npcManager == null) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "npc:server:unavailable")))
            return
        }

        val shopItems = npcManager.getSellerItems(npcId)
        if (shopItems == null) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "npc:server:not_seller")))
            return
        }

        val entry =
            shopItems.firstOrNull { it.itemType.equals(itemTypeName, ignoreCase = true) }
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(lang, "npc:server:item_not_in_shop", itemTypeName)))
                    return
                }

        val totalCost = entry.buyPrice * quantity
        val newWallet =
            runCatching { CurrencyUtils.deductCopper(session.state.wallet, totalCost) }
                .getOrElse {
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(lang, "npc:server:insufficient_funds")))
                    return
                }

        session.inventory.merge(ItemType(entry.itemType.uppercase()), quantity, Int::plus)
        session.state = session.state.copy(wallet = newWallet)
        context.savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(ServerMessage.WalletUpdate(newWallet))
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "npc:server:buy_done", quantity, entry.itemType.lowercase())))
    }
}
