package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.npc.CurrencyUtils
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.protocol.ServerMessage

class NpcSellCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d4e5f6a7-b8c9-0123-def4-567890abcdef")
    override val name = "npcsell"
    override val description = "Sell an item to a seller NPC."
    override val usage = "$command <npcId> <itemType> [quantity]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val parts = args.trim().split(Regex("\\s+"))
        val npcId = parts.getOrNull(0).orEmpty()
        val itemTypeName = parts.getOrNull(1).orEmpty()
        val quantity = parts.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        if (npcId.isBlank() || itemTypeName.isBlank()) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "npc:server:sell_usage")))
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

        if (entry.sellPrice <= 0) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "npc:server:item_not_sellable", itemTypeName)))
            return
        }

        val itemType = ItemType(entry.itemType.uppercase())
        val currentCount = session.inventory[itemType] ?: 0
        if (currentCount < quantity) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "npc:server:not_enough_items", itemTypeName)))
            return
        }

        val totalGain = entry.sellPrice * quantity
        val newWallet = CurrencyUtils.addCopper(session.state.wallet, totalGain)

        val newCount = currentCount - quantity
        if (newCount <= 0) session.inventory.remove(itemType)
        else session.inventory[itemType] = newCount

        session.state = session.state.copy(wallet = newWallet)
        context.savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(ServerMessage.WalletUpdate(newWallet))
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "npc:server:sell_done", quantity, entry.itemType.lowercase())))
    }
}
