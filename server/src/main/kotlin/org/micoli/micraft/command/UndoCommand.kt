package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.session.WorldActionRecord
import org.micoli.micraft.world.BlockType

class UndoCommand : CommandHandler {
    override val id: UUID = UUID.fromString("efe56d66-b31e-4e09-9898-1735149e6adf")
    override val command = "/undo"
    override val description =
        "Undo the last N block breaks, restoring blocks and reversing item collection."
    override val usage = "/undo [N]"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val n = args.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val history = session.actionHistory
        if (history.isEmpty()) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "undo:server:nothing")))
            return
        }
        val count = minOf(n, history.size)
        repeat(count) {
            when (val record = history.removeLast()) {
                is WorldActionRecord.Break -> {
                    val change = BlockChange(record.pos, record.blockType)
                    context.world.applyChange(change)
                    context.broadcast(ServerMessage.WorldUpdate(listOf(change)))
                    for (item in record.spawnedItems) {
                        val stillInWorld =
                            context.worldItems?.let { wim ->
                                val found = wim.hasItem(item.id)
                                if (found) {
                                    wim.despawnItem(item.id)
                                    true
                                } else false
                            } ?: false
                        if (!stillInWorld) {
                            val remaining = (session.inventory[item.type] ?: 0) - item.count
                            if (remaining <= 0) session.inventory.remove(item.type)
                            else session.inventory[item.type] = remaining
                        }
                    }
                }
                is WorldActionRecord.Place -> {
                    val change = BlockChange(record.pos, BlockType.AIR)
                    context.world.applyChange(change)
                    context.broadcast(ServerMessage.WorldUpdate(listOf(change)))
                    session.inventory.merge(record.itemType, 1, Int::plus)
                }
            }
        }
        context.savePlayer(session)
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        val doneKey = if (count == 1) "undo:server:done_one" else "undo:server:done_many"
        session.send(ServerMessage.Notification(context.i18n.t(lang, doneKey, count)))
    }
}
