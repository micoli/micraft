package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class UndoCommand : CommandHandler {
    override val command = "/undo"
    override val description = "Undo the last N block breaks, restoring blocks and reversing item collection."
    override val usage = "/undo [N]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val n = args.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val history = session.breakHistory
        if (history.isEmpty()) {
            session.send(ServerMessage.Notification("Nothing to undo."))
            return
        }
        val count = minOf(n, history.size)
        repeat(count) {
            val record = history.removeLast()
            val change = BlockChange(record.pos, record.blockType)
            context.world.applyChange(change)
            context.broadcast(ServerMessage.WorldUpdate(listOf(change)))
            for (item in record.spawnedItems) {
                val stillInWorld = context.worldItems?.let { wim ->
                    val found = wim.hasItem(item.id)
                    if (found) { wim.despawnItem(item.id); true } else false
                } ?: false
                if (!stillInWorld) {
                    val remaining = (session.inventory[item.type] ?: 0) - item.count
                    if (remaining <= 0) session.inventory.remove(item.type)
                    else session.inventory[item.type] = remaining
                }
            }
        }
        context.savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        session.send(ServerMessage.Notification("Undid $count block break${if (count > 1) "s" else ""}."))
    }
}
