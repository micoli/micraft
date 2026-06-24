package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.ItemType
import java.util.UUID

class GiveCommand : CommandHandler {
    override val id = UUID.fromString("84b05d3d-19c7-4cee-bb3d-469d053c9b07")
    override val command = "/give"
    override val description = "Give items to yourself."
    override val usage = "/give <itemType> [N]"
    override val options = ItemType.entries.map { it.name.lowercase() }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val parts = args.trim().split(Regex("\\s+"))
        val typeName = parts.getOrNull(0).orEmpty()

        if (typeName.isBlank()) {
            val available = ItemType.entries.joinToString(", ") { it.name.lowercase() }
            session.send(ServerMessage.Notification(context.i18n.t(lang, "give:server:usage", available)))
            return
        }

        val itemType = ItemType.entries.firstOrNull { it.name.equals(typeName, ignoreCase = true) }
        if (itemType == null) {
            val available = ItemType.entries.joinToString(", ") { it.name.lowercase() }
            session.send(ServerMessage.Notification(context.i18n.t(lang, "give:server:unknown", typeName, available)))
            return
        }

        val n = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        session.inventory.merge(itemType, n, Int::plus)
        context.savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(ServerMessage.Notification(context.i18n.t(lang, "give:server:done", n, itemType.name.lowercase())))
    }
}
