package org.micoli.micraft.tick

import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.session.WorldActionRecord
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.WorldState
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(BlockPlacer::class.java)
private const val MAX_PLACE_DISTANCE = 6.0
private const val MAX_ACTION_HISTORY = 20

class BlockPlacer(
    private val world: WorldState,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val savePlayer: (PlayerSession) -> Unit,
) {
    suspend fun handlePlace(session: PlayerSession, intent: ClientMessage.BlockPlace) {
        val pos = intent.pos
        val itemType = intent.itemType

        val blockType = itemType.placesBlock
        if (!itemType.buildable || blockType == null) {
            log.debug("BlockPlace rejected: {} not buildable", itemType)
            return
        }

        val count = session.inventory[itemType] ?: 0
        if (count <= 0) {
            log.debug("BlockPlace rejected: no {} in inventory", itemType)
            return
        }

        val eyeY = session.state.pos.y + session.state.stance.eyeOffset
        val dist = kotlin.math.sqrt(
            ((pos.x + 0.5f - session.state.pos.x) * (pos.x + 0.5f - session.state.pos.x) +
             (pos.y + 0.5f - eyeY) * (pos.y + 0.5f - eyeY) +
             (pos.z + 0.5f - session.state.pos.z) * (pos.z + 0.5f - session.state.pos.z)).toDouble()
        )
        if (dist > MAX_PLACE_DISTANCE) {
            log.debug("BlockPlace rejected: dist={} > {}", "%.2f".format(dist), MAX_PLACE_DISTANCE)
            return
        }

        val existing = world.getBlock(pos.x, pos.y, pos.z)
        if (existing != BlockType.AIR) {
            log.debug("BlockPlace rejected: pos={} is {}", pos, existing)
            return
        }

        val change = BlockChange(pos, blockType)
        world.applyChange(change)
        broadcast(ServerMessage.WorldUpdate(listOf(change)))

        val remaining = count - 1
        if (remaining <= 0) session.inventory.remove(itemType)
        else session.inventory[itemType] = remaining
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))

        session.actionHistory.addLast(WorldActionRecord.Place(pos, itemType))
        if (session.actionHistory.size > MAX_ACTION_HISTORY) session.actionHistory.removeFirst()

        log.debug("BlockPlace: {} placed {} at {}", session.state.name, itemType.placesBlock, pos)
    }

    suspend fun handleShortcutBarSet(session: PlayerSession, intent: ClientMessage.ShortcutBarSet) {
        val slot = intent.slot
        val itemType = intent.itemType

        if (slot !in 1..9) {
            log.debug("ShortcutBarSet rejected: slot {} out of range 1..9", slot)
            return
        }
        if (itemType != null && !itemType.buildable) {
            log.debug("ShortcutBarSet rejected: {} not buildable", itemType)
            return
        }

        session.shortcutBar[slot] = itemType
        savePlayer(session)
        session.send(ServerMessage.ShortcutBarUpdate(session.shortcutBar.toList()))
    }
}
