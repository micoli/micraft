package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.protocol.ServerMessage

class GiveCommand : CommandHandler {
    override val id: UUID = UUID.fromString("84b05d3d-19c7-4cee-bb3d-469d053c9b07")
    override val name = "give"
    override val permission = "admin"
    override val description = "Give items to yourself."
    override val usage = "$command <itemType> [N]"
    override val options
        get() = ItemRegistry.keys().map { it.id.lowercase() }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val parts = args.trim().split(Regex("\\s+"))
        val typeName = parts.getOrNull(0).orEmpty()

        if (typeName.isBlank()) {
            val available = ItemRegistry.keys().joinToString(", ") { it.id.lowercase() }
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "give:server:usage", available)))
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
        if (itemType == null) {
            val available = ItemRegistry.keys().joinToString(", ") { it.id.lowercase() }
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "give:server:unknown", typeName, available)))
            return
        }

        val n = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        session.inventory.merge(itemType, n, Int::plus)
        context.savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "give:server:done", n, itemType.id.lowercase())))
    }
}
