package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.rpg.DerivedStatsCalculator
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.protocol.ServerMessage

class DrinkCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a3f2c1d4-8b7e-4f56-9c3a-2e1d0b5f6a7c")
    override val name = "drink"
    override val permission = "player"
    override val description = "Consomme un item consommable de l'inventaire."
    override val usage = "$command <itemType>"
    override val options
        get() =
            ItemRegistry.keys()
                .filter {
                    ItemRegistry.get(it).let { d -> d.healthRestore > 0 || d.manaRestore > 0 }
                }
                .map { it.id.lowercase() }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val typeName = args.trim()
        if (typeName.isBlank()) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "drink:server:usage")))
            return
        }
        val itemType = ItemRegistry.keys().firstOrNull { it.id.equals(typeName, ignoreCase = true) }
        if (itemType == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "drink:server:unknown", typeName)))
            return
        }
        val def = ItemRegistry.get(itemType)
        if (def.healthRestore == 0 && def.manaRestore == 0) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "drink:server:not_consumable", typeName)))
            return
        }
        val charData =
            session.characterData
                ?: run {
                    session.send(
                        ServerMessage.Notification(
                            context.i18n.t(lang, "drink:server:no_character")))
                    return
                }
        val qty = session.inventory[itemType] ?: 0
        if (qty <= 0) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "drink:server:not_in_inventory", typeName)))
            return
        }
        if (qty == 1) session.inventory.remove(itemType) else session.inventory[itemType] = qty - 1
        val derived = DerivedStatsCalculator.compute(charData, emptyList())
        val newCharData =
            charData.copy(
                currentHp = (charData.currentHp + def.healthRestore).coerceAtMost(derived.maxHp),
                currentMana =
                    (charData.currentMana + def.manaRestore).coerceAtMost(derived.maxMana),
            )
        session.characterData = newCharData
        context.savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        context.sendStatusUpdate?.invoke(session)
        session.send(
            ServerMessage.Notification(context.i18n.t(lang, "drink:server:consumed", typeName)))
    }
}
