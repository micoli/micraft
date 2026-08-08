package org.micoli.micraft.game.world.block

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.MAX_INTERACTION_DISTANCE
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.WorldActionRecord
import org.micoli.micraft.game.session.toPageMap
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.instance.InstanceRegistry
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
    @Volatile private var attackRegistry: Map<String, AttackDefinition> = emptyMap(),
    private val instanceRegistry: InstanceRegistry? = null,
) {
    suspend fun handlePlace(session: PlayerSession, intent: ClientMessage.BlockPlace) {
        val rawPos = intent.pos

        if (instanceRegistry?.zoneAt(rawPos.x, rawPos.y, rawPos.z) != null) {
            blockPlacerLog.debug("BlockPlace rejected: pos={} is inside a protected zone", rawPos)
            return
        }

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
                ((rawPos.x + 0.5f - session.state.pos.x) * (rawPos.x + 0.5f - session.state.pos.x) +
                        (rawPos.y + 0.5f - eyeY) * (rawPos.y + 0.5f - eyeY) +
                        (rawPos.z + 0.5f - session.state.pos.z) *
                            (rawPos.z + 0.5f - session.state.pos.z))
                    .toDouble())
        if (dist > MAX_INTERACTION_DISTANCE) {
            blockPlacerLog.debug(
                "BlockPlace rejected: dist={} > {}", "%.2f".format(dist), MAX_INTERACTION_DISTANCE)
            return
        }

        val def = blockType.let { BlockRegistry.get(it) }

        // Color comes from the item definition, never from the client-sent state byte.
        val colorIndex =
            if (def.plainColorable)
                PlainColorRegistry.indexOf(ItemRegistry.get(itemType).plainColor)
            else 0
        val rotation = BlockState.rotation(intent.state)
        val state = BlockState.pack(rotation, colorIndex)

        val brickSizeX = def.brickSize.getOrElse(0) { 1f }
        val brickSizeY = def.brickSize.getOrElse(1) { 1f }
        val brickSizeZ = def.brickSize.getOrElse(2) { 1f }

        // Swap X/Z for 90°/270° rotations
        val effectiveSizeX = if (rotation % 2 == 0) brickSizeX else brickSizeZ
        val effectiveSizeZ = if (rotation % 2 == 0) brickSizeZ else brickSizeX

        val sizeX = ceil(effectiveSizeX).toInt().coerceAtLeast(1)
        val sizeY = ceil(brickSizeY).toInt().coerceAtLeast(1)
        val sizeZ = ceil(effectiveSizeZ).toInt().coerceAtLeast(1)

        val slotsX = if (effectiveSizeX < 1.0f) floor(1.0f / effectiveSizeX).toInt() else 1
        val slotsZ = if (effectiveSizeZ < 1.0f) floor(1.0f / effectiveSizeZ).toInt() else 1
        val isXZFractional = slotsX > 1 || slotsZ > 1

        // Client computes world-space offsets (effectiveFracX already accounts for rotation)
        val xOffset = intent.xOffset.toInt() and 0xFF
        val zOffset = intent.zOffset.toInt() and 0xFF

        val isFractional = def.heightFraction < 1.0f

        // For solid multi-cell entities: redirect placement to master when clicking a satellite top
        val belowMaster =
            if (!isFractional) world.getEntityMasterWorldPos(rawPos.x, rawPos.y - 1, rawPos.z)
            else null
        val solidPos =
            if (belowMaster != null &&
                belowMaster != BlockPos(rawPos.x, rawPos.y - 1, rawPos.z) &&
                BlockRegistry.get(world.getBlock(belowMaster.x, belowMaster.y, belowMaster.z))
                    .heightFraction >= 1.0f) {
                BlockPos(belowMaster.x, rawPos.y, belowMaster.z)
            } else rawPos

        // For fractional plates: redirect to fractional master for sub-voxel stacking.
        // Handles two cases:
        //   (a) pos is a satellite of a fractional entity (click from side) → master
        //   (b) pos is AIR above a plate stud (click from top) → plate master at y-1
        val pos =
            if (isFractional) {
                val directMaster = world.getEntityMasterWorldPos(solidPos.x, solidPos.y, solidPos.z)
                when {
                    directMaster != null -> {
                        val masterOffsets =
                            world.getFractionalYOffsetsAt(
                                directMaster.x, directMaster.y, directMaster.z)
                        if (masterOffsets.isNotEmpty() && masterOffsets.size < 3) directMaster
                        else solidPos
                    }
                    solidPos.y > 0 &&
                        world.getBlock(solidPos.x, solidPos.y, solidPos.z) == BlockType.AIR -> {
                        val belowY = solidPos.y - 1
                        val directOffsets =
                            world.getFractionalYOffsetsAt(solidPos.x, belowY, solidPos.z)
                        when {
                            directOffsets.isNotEmpty() && directOffsets.size < 3 ->
                                BlockPos(solidPos.x, belowY, solidPos.z)
                            else -> {
                                val satMaster =
                                    world.getEntityMasterWorldPos(solidPos.x, belowY, solidPos.z)
                                if (satMaster != null) {
                                    val masterOffsets =
                                        world.getFractionalYOffsetsAt(
                                            satMaster.x, satMaster.y, satMaster.z)
                                    if (masterOffsets.isNotEmpty() && masterOffsets.size < 3)
                                        satMaster
                                    else solidPos
                                } else solidPos
                            }
                        }
                    }
                    else -> solidPos
                }
            } else solidPos

        if (isXZFractional) {
            // XZ-fractional block (e.g. arch): multiple fit per voxel along X or Z axis
            val existing = world.getBlock(pos.x, pos.y, pos.z)
            // Master cell must be empty or already occupied by the same block type
            if (existing != BlockType.AIR && existing != blockType) {
                blockPlacerLog.debug(
                    "BlockPlace rejected: XZ-frac pos={} has different block {}", pos, existing)
                return
            }
            val usedSlots = world.getXZOffsetsAt(pos.x, pos.y, pos.z)
            // For sub-voxel axes (slots > 1), validate slot index; for long axes (slots == 1),
            // xOffset/zOffset is a stud-position (0 = flush, 1 = +0.5 block) — range check skipped
            if ((slotsX > 1 && xOffset >= slotsX) || (slotsZ > 1 && zOffset >= slotsZ)) {
                blockPlacerLog.debug(
                    "BlockPlace rejected: XZ-frac offset {},{} out of slots {},{}",
                    xOffset,
                    zOffset,
                    slotsX,
                    slotsZ)
                return
            }
            // Collision key normalizes long-axis stud positions to 0 (only one brick per slot pair)
            val slotKeyX = if (slotsX > 1) xOffset else 0
            val slotKeyZ = if (slotsZ > 1) zOffset else 0
            val slotOccupied =
                usedSlots.any { (storedX, storedZ) ->
                    (if (slotsX > 1) storedX else 0) == slotKeyX &&
                        (if (slotsZ > 1) storedZ else 0) == slotKeyZ
                }
            if (slotOccupied) {
                blockPlacerLog.debug(
                    "BlockPlace rejected: XZ-frac slot {},{} already occupied", slotKeyX, slotKeyZ)
                return
            }
            // Validate satellite cells (full cells spanning integer multiples)
            if (sizeX > 1 || sizeZ > 1) {
                for (dx in 0 until sizeX) for (dz in 0 until sizeZ) {
                    if (dx == 0 && dz == 0) continue
                    val cx = pos.x + dx
                    val cz = pos.z + dz
                    val satBlock = world.getBlock(cx, pos.y, cz)
                    if (satBlock != BlockType.AIR && satBlock != blockType) {
                        blockPlacerLog.debug(
                            "BlockPlace rejected: XZ-frac satellite {},{},{} has block {}",
                            cx,
                            pos.y,
                            cz,
                            satBlock)
                        return
                    }
                }
            }
            val changes = mutableListOf<BlockChange>()
            val isFirstAtCell = existing == BlockType.AIR
            if (isFirstAtCell) {
                val masterChange = BlockChange(pos, blockType, state)
                world.applyChange(masterChange)
                changes.add(masterChange)
                if (sizeX > 1 || sizeZ > 1) {
                    for (dx in 0 until sizeX) for (dz in 0 until sizeZ) {
                        if (dx == 0 && dz == 0) continue
                        val satChange =
                            BlockChange(BlockPos(pos.x + dx, pos.y, pos.z + dz), blockType, state)
                        world.applyChange(satChange)
                        changes.add(satChange)
                    }
                }
            }
            val proto =
                BlockEntityProto(
                    worldX = pos.x,
                    worldY = pos.y,
                    worldZ = pos.z,
                    type = blockType.id,
                    sizeX = sizeX,
                    sizeY = sizeY,
                    sizeZ = sizeZ,
                    rotation = rotation,
                    yOffset = 0,
                    xOffset = xOffset,
                    zOffset = zOffset,
                    colorIndex = colorIndex,
                )
            world.applyEntityAdd(proto)
            broadcast(ServerMessage.WorldUpdate(changes, entityAdds = listOf(proto)))
        } else if (isFractional) {
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
                    rotation = rotation,
                    yOffset = nextOffset,
                    colorIndex = colorIndex,
                )
            val changes = mutableListOf<BlockChange>()
            if (nextOffset == 0) {
                // First plate at this cell: set block type so main renderer sees it
                val change = BlockChange(pos, blockType, state)
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

            val changes = mutableListOf<BlockChange>()
            val masterChange = BlockChange(pos, blockType, state)
            world.applyChange(masterChange)
            changes.add(masterChange)

            if (isMultiCell) {
                // Write block type to satellite cells so raycast can detect them
                for (dx in 0 until sizeX) for (dy in 0 until sizeY) for (dz in 0 until sizeZ) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    val satChange =
                        BlockChange(BlockPos(pos.x + dx, pos.y + dy, pos.z + dz), blockType, state)
                    world.applyChange(satChange)
                    changes.add(satChange)
                }
                val proto =
                    BlockEntityProto(
                        worldX = pos.x,
                        worldY = pos.y,
                        worldZ = pos.z,
                        type = blockType.id,
                        sizeX = sizeX,
                        sizeY = sizeY,
                        sizeZ = sizeZ,
                        rotation = rotation,
                        colorIndex = colorIndex,
                        xOffset = xOffset,
                        zOffset = zOffset,
                    )
                world.applyEntityAdd(proto)
                broadcast(ServerMessage.WorldUpdate(changes, entityAdds = listOf(proto)))
            } else {
                broadcast(ServerMessage.WorldUpdate(changes))
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
            is ShortcutSlot.Item -> {
                val def = ItemRegistry.get(content.itemType)
                if (!content.itemType.buildable && !def.consumable) {
                    blockPlacerLog.debug(
                        "ShortcutBarSet rejected: {} not buildable or consumable", content.itemType)
                    return
                }
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

    fun reload(attackRegistry: Map<String, AttackDefinition>) {
        this.attackRegistry = attackRegistry
    }
}
