package org.micoli.micraft.game.world.block

import kotlin.math.sqrt
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.MAX_INTERACTION_DISTANCE
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.WorldActionRecord
import org.micoli.micraft.game.session.toPageMap
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.BlockEntityProto
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val blockPlacerLog = LoggerFactory.getLogger(BlockPlacer::class.java)
private const val MAX_ACTION_HISTORY = 20

class BlockPlacer(
    private val world: WorldState,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val savePlayer: (PlayerSession) -> Unit,
    private val vegetationManager: VegetationManager? = null,
    private val attackRegistry: Map<String, AttackDefinition> = emptyMap(),
) {
    suspend fun handlePlace(session: PlayerSession, intent: ClientMessage.BlockPlace) {
        val pos = intent.pos
        val itemType = intent.itemType

        val blockType = itemType.placesBlock
        if (!itemType.buildable || blockType == null) {
            blockPlacerLog.debug("BlockPlace rejected: {} not buildable", itemType)
            return
        }

        val count = session.inventory[itemType] ?: 0
        if (count <= 0) {
            blockPlacerLog.debug("BlockPlace rejected: no {} in inventory", itemType)
            return
        }

        val eyeY = session.state.pos.y + session.state.stance.eyeOffset
        val dist =
            sqrt(
                ((pos.x + 0.5f - session.state.pos.x) * (pos.x + 0.5f - session.state.pos.x) +
                        (pos.y + 0.5f - eyeY) * (pos.y + 0.5f - eyeY) +
                        (pos.z + 0.5f - session.state.pos.z) * (pos.z + 0.5f - session.state.pos.z))
                    .toDouble())
        if (dist > MAX_INTERACTION_DISTANCE) {
            blockPlacerLog.debug(
                "BlockPlace rejected: dist={} > {}", "%.2f".format(dist), MAX_INTERACTION_DISTANCE)
            return
        }

        val def = blockType.let { BlockRegistry.get(it) }
        val (sizeX, sizeY, sizeZ) =
            if (def.brickSize.size == 3)
                Triple(def.brickSize[0], def.brickSize[1], def.brickSize[2])
            else Triple(1, 1, 1)

        val isFractional = def.heightFraction < 1.0f

        if (isFractional) {
            // Fractional block (plate): stacking allowed — cell may already contain a plate
            val existing = world.getBlock(pos.x, pos.y, pos.z)
            if (existing != BlockType.AIR && BlockRegistry.get(existing).heightFraction >= 1.0f) {
                blockPlacerLog.debug(
                    "BlockPlace rejected: pos={} has solid non-fractional block", pos)
                return
            }
            val usedOffsets = world.getFractionalYOffsetsAt(pos.x, pos.y, pos.z)
            // If hasEntityAt is true but no fractional masters → non-plate entity/satellite present
            if (world.hasEntityAt(pos.x, pos.y, pos.z) && usedOffsets.isEmpty()) {
                blockPlacerLog.debug("BlockPlace rejected: pos={} has non-plate entity", pos)
                return
            }
            val nextOffset = (0..2).firstOrNull { it !in usedOffsets }
            if (nextOffset == null) {
                blockPlacerLog.debug("BlockPlace rejected: pos={} plate cell full", pos)
                return
            }
            // Validate satellite cells for multi-cell footprint
            val isMultiCellFootprint = sizeX > 1 || sizeZ > 1
            if (isMultiCellFootprint) {
                for (dx in 0 until sizeX) for (dz in 0 until sizeZ) {
                    if (dx == 0 && dz == 0) continue
                    val cx = pos.x + dx
                    val cz = pos.z + dz
                    val satBlock = world.getBlock(cx, pos.y, cz)
                    // Reject if cell has a solid non-fractional block
                    if (satBlock != BlockType.AIR &&
                        BlockRegistry.get(satBlock).heightFraction >= 1.0f) {
                        blockPlacerLog.debug(
                            "BlockPlace rejected: plate satellite {},{},{} has solid block",
                            cx,
                            pos.y,
                            cz)
                        return
                    }
                    // Reject only if entity at satellite belongs to a DIFFERENT master
                    val satMasterPos = world.getEntityMasterWorldPos(cx, pos.y, cz)
                    if (satMasterPos != null && satMasterPos != pos) {
                        blockPlacerLog.debug(
                            "BlockPlace rejected: plate satellite {},{},{} has entity from different master",
                            cx,
                            pos.y,
                            cz)
                        return
                    }
                    // If satMasterPos == pos: satellite of our plate at a previous yOffset →
                    // nextOffset
                    // not in usedOffsets (already validated above for master cell)
                }
            }
            val proto =
                BlockEntityProto(
                    worldX = pos.x,
                    worldY = pos.y,
                    worldZ = pos.z,
                    type = blockType.id,
                    sizeX = sizeX,
                    sizeY = 1,
                    sizeZ = sizeZ,
                    rotation = intent.state.toInt() and 0x03,
                    yOffset = nextOffset,
                )
            val changes = mutableListOf<BlockChange>()
            if (nextOffset == 0) {
                // First plate at this cell: set block type so main renderer sees it
                val change = BlockChange(pos, blockType, intent.state)
                world.applyChange(change)
                changes.add(change)
            }
            world.applyEntityAdd(proto)
            broadcast(ServerMessage.WorldUpdate(changes, entityAdds = listOf(proto)))
        } else {
            val existing = world.getBlock(pos.x, pos.y, pos.z)
            if (!existing.isReplaceable) {
                blockPlacerLog.debug("BlockPlace rejected: pos={} is {}", pos, existing)
                return
            }
            val isMultiCell = sizeX > 1 || sizeY > 1 || sizeZ > 1
            if (isMultiCell) {
                for (dx in 0 until sizeX) for (dy in 0 until sizeY) for (dz in 0 until sizeZ) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    val cx = pos.x + dx
                    val cy = pos.y + dy
                    val cz = pos.z + dz
                    val cellBlock = world.getBlock(cx, cy, cz)
                    if (!cellBlock.isReplaceable) {
                        blockPlacerLog.debug(
                            "BlockPlace rejected: multi-cell pos={},{},{} occupied by {}",
                            cx,
                            cy,
                            cz,
                            cellBlock)
                        return
                    }
                    if (world.hasEntityAt(cx, cy, cz)) {
                        blockPlacerLog.debug(
                            "BlockPlace rejected: multi-cell pos={},{},{} has entity", cx, cy, cz)
                        return
                    }
                }
            }
            if (world.hasEntityAt(pos.x, pos.y, pos.z)) {
                blockPlacerLog.debug("BlockPlace rejected: pos={} has entity", pos)
                return
            }

            val change = BlockChange(pos, blockType, intent.state)
            world.applyChange(change)

            if (isMultiCell) {
                val proto =
                    BlockEntityProto(
                        worldX = pos.x,
                        worldY = pos.y,
                        worldZ = pos.z,
                        type = blockType.id,
                        sizeX = sizeX,
                        sizeY = sizeY,
                        sizeZ = sizeZ,
                        rotation = intent.state.toInt() and 0x03,
                    )
                world.applyEntityAdd(proto)
                broadcast(ServerMessage.WorldUpdate(listOf(change), entityAdds = listOf(proto)))
            } else {
                broadcast(ServerMessage.WorldUpdate(listOf(change)))
            }
        }
        vegetationManager?.tryActivate(pos, blockType)

        val remaining = count - 1
        if (remaining <= 0) session.inventory.remove(itemType)
        else session.inventory[itemType] = remaining
        session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))

        session.actionHistory.addLast(WorldActionRecord.Place(pos, itemType))
        if (session.actionHistory.size > MAX_ACTION_HISTORY) session.actionHistory.removeFirst()

        blockPlacerLog.debug(
            "BlockPlace: {} placed {} at {}", session.state.name, itemType.placesBlock, pos)
    }

    suspend fun handleShortcutBarSet(session: PlayerSession, intent: ClientMessage.ShortcutBarSet) {
        val page = intent.page
        val slot = intent.slot
        val content = intent.content

        if (page !in 0..9) {
            blockPlacerLog.debug("ShortcutBarSet rejected: page {} out of range 0..9", page)
            return
        }
        if (slot !in 1..9) {
            blockPlacerLog.debug("ShortcutBarSet rejected: slot {} out of range 1..9", slot)
            return
        }
        when (content) {
            is ShortcutSlot.Item ->
                if (!content.itemType.buildable) {
                    blockPlacerLog.debug(
                        "ShortcutBarSet rejected: {} not buildable", content.itemType)
                    return
                }
            is ShortcutSlot.Attack ->
                if (!attackRegistry.containsKey(content.attackId)) {
                    blockPlacerLog.debug(
                        "ShortcutBarSet rejected: unknown attack {}", content.attackId)
                    return
                }
            is ShortcutSlot.Macro ->
                if (content.macroName.isBlank()) {
                    blockPlacerLog.debug("ShortcutBarSet rejected: blank macro name")
                    return
                }
            is ShortcutSlot.Spell ->
                if (content.spellId.isBlank()) {
                    blockPlacerLog.debug("ShortcutBarSet rejected: blank spell id")
                    return
                }
            null -> {}
        }

        session.shortcutBarPages[page][slot] = content
        savePlayer(session)
        session.send(ServerMessage.ShortcutBarUpdate(session.shortcutBarPages.toPageMap()))
    }
}
