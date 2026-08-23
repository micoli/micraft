package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.protocol.ServerMessage.Notification

class GiveCommand : CommandHandler {
    override val id: UUID = UUID.fromString("84b05d3d-19c7-4cee-bb3d-469d053c9b07")
    override val name = "give"
    override val permission = "admin"
    override val description = "Give an item, or grant an armor/weapon/tool, to yourself."
    override val usage = "$command <name> [N]"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        if (argIndex == 0)
            (ItemRegistry.keys().map { it.id.lowercase() } +
                    context.armorRegistry().keys +
                    context.weaponRegistry().keys +
                    context.toolRegistry().keys)
                .filter { it.contains(partial, ignoreCase = true) }
        else emptyList()

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val parts = args.trim().split(Regex("\\s+"))
        val typeName = parts.getOrNull(0).orEmpty()

        if (typeName.isBlank()) {
            val available = ItemRegistry.keys().joinToString(", ") { it.id.lowercase() }
            session.send(Notification(context.i18n.t(lang, "give:server:usage", available)))
            return
        }

        val itemType =
            ItemRegistry.keys().firstOrNull { it.id.equals(typeName, ignoreCase = true) }
                ?: run {
                    val blockType = BlockType(typeName.uppercase())
                    ItemRegistry.keys().firstOrNull {
                        ItemRegistry.get(it).placesBlock == blockType
                    }
                }

        if (itemType != null) {
            val n = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            session.inventory.merge(itemType, n, Int::plus)
            context.savePlayer(session)
            session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
            session.send(
                Notification(context.i18n.t(lang, "give:server:done", n, itemType.id.lowercase())))
            return
        }

        val equipmentName =
            (context.armorRegistry().keys +
                    context.weaponRegistry().keys +
                    context.toolRegistry().keys)
                .firstOrNull { it.equals(typeName, ignoreCase = true) }
        if (equipmentName == null) {
            val available =
                (ItemRegistry.keys().map { it.id.lowercase() } +
                        context.armorRegistry().keys +
                        context.weaponRegistry().keys +
                        context.toolRegistry().keys)
                    .joinToString(", ")
            session.send(
                Notification(context.i18n.t(lang, "give:server:unknown", typeName, available)))
            return
        }

        if (equipmentName in session.state.ownedArmors ||
            equipmentName in session.state.ownedWeapons ||
            equipmentName in session.state.ownedTools) {
            session.send(Notification(context.i18n.t(lang, "give:server:already", equipmentName)))
            return
        }

        session.state =
            when {
                equipmentName in context.armorRegistry() ->
                    session.state.copy(ownedArmors = session.state.ownedArmors + equipmentName)
                equipmentName in context.weaponRegistry() ->
                    session.state.copy(ownedWeapons = session.state.ownedWeapons + equipmentName)
                else -> session.state.copy(ownedTools = session.state.ownedTools + equipmentName)
            }
        context.savePlayer(session)
        session.send(Notification(context.i18n.t(lang, "give:server:done", 1, equipmentName)))
    }
}
