package org.micoli.micraft.game.world.block

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import org.micoli.micraft.combat.AttackDefinition
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.MAX_INTERACTION_DISTANCE
import org.micoli.micraft.game.placeable.PlaceableManager
import org.micoli.micraft.game.placeable.siege.SiegeWeaponManager
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.WorldActionRecord
import org.micoli.micraft.game.session.toPageMap
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.EntityType
import org.micoli.micraft.game.world.ItemRegistry
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.PlainColorRegistry
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.rail.RailNetworkRegistry
import org.micoli.micraft.game.world.vegetation.VegetationManager
import org.micoli.micraft.placeable.PlaceableRegistry
import org.micoli.micraft.player.EditMode
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
    private val railNetworkRegistry: RailNetworkRegistry? = null,
    private val placeableManager: PlaceableManager? = null,
    private val siegeWeaponManager: SiegeWeaponManager? = null,
) {
    /**
     * `spawnsEntity` items delegate to [PlaceableManager] instead of placing a block — ground
     * validity (solid, non-liquid, non-rail) is checked by [PlaceableManager.spawn] itself. If the
     * spawned type is also a siege weapon, [SiegeWeaponManager] links its own instance right after.
     */
    private suspend fun handleSpawnPlaceable(
        session: PlayerSession,
        rawPos: BlockPos,
        itemType: ItemType,
        entityType: EntityType,
    ) {
        val manager = placeableManager
        if (manager == null) {
            blockPlacerLog.debug("BlockPlace(spawnsEntity) rejected: no PlaceableManager wired")
            return
        }

        val creative = session.state.editMode == EditMode.CREATIVE
        val count = session.inventory[itemType] ?: 0
        if (!creative && count <= 0) {
            blockPlacerLog.debug("BlockPlace(spawnsEntity) rejected: no {} in inventory", itemType)
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
        if (!creative && dist > MAX_INTERACTION_DISTANCE) {
            blockPlacerLog.debug(
                "BlockPlace(spawnsEntity) rejected: dist={} > {}",
                "%.2f".format(dist),
                MAX_INTERACTION_DISTANCE)
            return
        }

        val spawned = manager.spawn(entityType, rawPos, world)
        if (spawned == null) {
            blockPlacerLog.debug("BlockPlace(spawnsEntity) rejected: invalid ground at {}", rawPos)
            return
        }

        siegeWeaponManager?.spawnFor(spawned)

        if (!creative) {
            val remaining = count - 1
            if (remaining <= 0) session.inventory.remove(itemType)
            else session.inventory[itemType] = remaining
            session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        }

        blockPlacerLog.debug(
            "BlockPlace(spawnsEntity): {} spawned {} at {}", session.state.name, entityType, rawPos)
    }

    suspend fun handlePlace(session: PlayerSession, intent: ClientMessage.BlockPlace) {
        val rawPos = intent.pos

        if (instanceRegistry?.zoneAt(rawPos.x, rawPos.y, rawPos.z) != null) {
            blockPlacerLog.debug("BlockPlace rejected: pos={} is inside a protected zone", rawPos)
            return
        }

        val itemType = intent.itemType

        val spawnedEntityType = ItemRegistry.get(itemType).spawnsEntity
        if (spawnedEntityType != null && PlaceableRegistry.get(spawnedEntityType) != null) {
            handleSpawnPlaceable(session, rawPos, itemType, spawnedEntityType)
            return
        }

        val blockType = itemType.placesBlock
        if (!itemType.buildable || blockType == null) {
            blockPlacerLog.debug("BlockPlace rejected: {} not buildable", itemType)
            return
        }

        val creative = session.state.editMode == EditMode.CREATIVE
        val count = session.inventory[itemType] ?: 0
        if (!creative && count <= 0) {
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
        if (!creative && dist > MAX_INTERACTION_DISTANCE) {
            blockPlacerLog.debug(
                "BlockPlace rejected: dist={} > {}", "%.2f".format(dist), MAX_INTERACTION_DISTANCE)
            return
        }

        val def = BlockRegistry.get(blockType)

        // Color comes from the item definition, never from the client-sent state byte.
        val colorIndex =
            if (def.plainColorable)
                PlainColorRegistry.indexOf(ItemRegistry.get(itemType).plainColor)
            else 0
        val rotation = BlockState.rotation(intent.state)
        val xOffset = intent.xOffset.toInt() and 0xFF
        val zOffset = intent.zOffset.toInt() and 0xFF

        val result =
            placeAt(
                rawPos, blockType, rotation, colorIndex, xOffset, zOffset, world, intent.extraState)
        if (result.rejectedReason != null) {
            blockPlacerLog.debug("BlockPlace rejected: {}", result.rejectedReason)
            return
        }
        if (result.changes.isNotEmpty() || result.entityAdds.isNotEmpty()) {
            broadcast(ServerMessage.WorldUpdate(result.changes, entityAdds = result.entityAdds))
        }
        result.changes.forEach { railNetworkRegistry?.invalidate(it.pos) }
        vegetationManager?.tryActivate(result.pos, blockType)

        if (!creative) {
            val remaining = count - 1
            if (remaining <= 0) session.inventory.remove(itemType)
            else session.inventory[itemType] = remaining
            session.send(ServerMessage.InventoryUpdate(session.inventory.toMap()))
        }

        session.actionHistory.addLast(WorldActionRecord.Place(result.pos, itemType))
        if (session.actionHistory.size > MAX_ACTION_HISTORY) session.actionHistory.removeFirst()

        blockPlacerLog.debug(
            "BlockPlace: {} placed {} at {}", session.state.name, itemType.placesBlock, result.pos)
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

    companion object {
        data class PlaceResult(
            val pos: BlockPos,
            val changes: List<BlockChange>,
            val entityAdds: List<BlockEntityProto>,
            val rejectedReason: String? = null,
        )

        /**
         * Resolves placement (including redirect-to-master for stacking), validates occupancy,
         * mutates [world] accordingly and returns what changed. Shared by the game client's
         * [handlePlace] (session/inventory/distance checks happen before this call) and the admin
         * instance editor (which skips those checks).
         */
        fun placeAt(
            rawPos: BlockPos,
            blockType: BlockType,
            rotation: Int,
            colorIndex: Int,
            xOffset: Int,
            zOffset: Int,
            world: BlockStore,
            extraState: Byte = 0,
        ): PlaceResult {
            val def = BlockRegistry.get(blockType)
            val state = BlockState.pack(rotation, colorIndex)

            // brickSize is expressed in half-voxel units: 2f = 1 full voxel.
            val brickSizeX = def.brickSize.getOrElse(0) { 2f }
            val brickSizeY = def.brickSize.getOrElse(1) { 2f }
            val brickSizeZ = def.brickSize.getOrElse(2) { 2f }

            // Swap X/Z for 90°/270° rotations
            val effectiveSizeX = if (rotation % 2 == 0) brickSizeX else brickSizeZ
            val effectiveSizeZ = if (rotation % 2 == 0) brickSizeZ else brickSizeX

            val sizeX = ceil(effectiveSizeX / 2.0f).toInt().coerceAtLeast(1)
            val sizeY = ceil(brickSizeY / 2.0f).toInt().coerceAtLeast(1)
            val sizeZ = ceil(effectiveSizeZ / 2.0f).toInt().coerceAtLeast(1)

            val gridSlotsX = if (effectiveSizeX < 2.0f) floor(2.0f / effectiveSizeX).toInt() else 1
            val gridSlotsZ = if (effectiveSizeZ < 2.0f) floor(2.0f / effectiveSizeZ).toInt() else 1
            val isXZFractionalBlock = gridSlotsX > 1 || gridSlotsZ > 1

            // Fine-snap: force the finer 1/4-voxel (4-slot) grid — even for a block whose own
            // brickSize would otherwise sit on the full grid (1 slot) — when a misaligned lego
            // neighbor is already present at the target cell or an adjacent cell, so the new
            // placement doesn't silently collapse onto the coarse grid next to an offset structure.
            val neighborForcesFineSnap =
                !isXZFractionalBlock && world.hasMisalignedNeighbor(rawPos.x, rawPos.y, rawPos.z)
            val slotsX =
                if (isXZFractionalBlock) gridSlotsX else if (neighborForcesFineSnap) 4 else 1
            val slotsZ =
                if (isXZFractionalBlock) gridSlotsZ else if (neighborForcesFineSnap) 4 else 1
            val isXZFractional = isXZFractionalBlock || neighborForcesFineSnap

            // Collision key normalizes long-axis stud positions to 0 (only one brick per slot pair)
            val slotKeyX = if (slotsX > 1) xOffset else 0
            val slotKeyZ = if (slotsZ > 1) zOffset else 0

            val isFractional = brickSizeY < 2.0f
            // Number of this block that stack within one voxel's height (e.g. brickSize[1]=0.5
            // → 4 slots).
            val maxYSlots =
                if (isFractional) floor(2.0f / brickSizeY).toInt().coerceAtLeast(1) else 1

            // For solid multi-cell entities: redirect placement to master when clicking a
            // satellite top
            val belowMaster =
                if (!isFractional) world.getEntityMasterWorldPos(rawPos.x, rawPos.y - 1, rawPos.z)
                else null
            val solidPos =
                if (belowMaster != null &&
                    belowMaster != BlockPos(rawPos.x, rawPos.y - 1, rawPos.z) &&
                    BlockRegistry.get(world.getBlock(belowMaster.x, belowMaster.y, belowMaster.z))
                        .brickSize[1] >= 2.0f) {
                    BlockPos(belowMaster.x, rawPos.y, belowMaster.z)
                } else rawPos

            // For fractional plates: redirect to fractional master for sub-voxel stacking.
            // Handles two cases:
            //   (a) pos is a satellite of a fractional entity (click from side) → master
            //   (b) pos is AIR above a plate stud (click from top) → plate master at y-1
            val pos =
                if (isFractional) {
                    val directMaster =
                        world.getEntityMasterWorldPos(solidPos.x, solidPos.y, solidPos.z)
                    when {
                        directMaster != null -> {
                            val masterOffsets =
                                world.getFractionalYOffsetsAt(
                                    directMaster.x,
                                    directMaster.y,
                                    directMaster.z,
                                    slotKeyX,
                                    slotKeyZ)
                            if (masterOffsets.isNotEmpty() && masterOffsets.size < maxYSlots)
                                directMaster
                            else solidPos
                        }
                        solidPos.y > 0 &&
                            world.getBlock(solidPos.x, solidPos.y, solidPos.z) == BlockType.AIR -> {
                            val belowY = solidPos.y - 1
                            val directOffsets =
                                world.getFractionalYOffsetsAt(
                                    solidPos.x, belowY, solidPos.z, slotKeyX, slotKeyZ)
                            when {
                                directOffsets.isNotEmpty() && directOffsets.size < maxYSlots ->
                                    BlockPos(solidPos.x, belowY, solidPos.z)
                                else -> {
                                    val satMaster =
                                        world.getEntityMasterWorldPos(
                                            solidPos.x, belowY, solidPos.z)
                                    if (satMaster != null) {
                                        val masterOffsets =
                                            world.getFractionalYOffsetsAt(
                                                satMaster.x,
                                                satMaster.y,
                                                satMaster.z,
                                                slotKeyX,
                                                slotKeyZ)
                                        if (masterOffsets.isNotEmpty() &&
                                            masterOffsets.size < maxYSlots)
                                            satMaster
                                        else solidPos
                                    } else solidPos
                                }
                            }
                        }
                        else -> solidPos
                    }
                } else solidPos

            if (isXZFractional && isFractional) {
                // XZ-fractional AND Y-stacking block (e.g. LEGO_PIECE): several fit per voxel
                // along X or Z axis, and each XZ sub-slot independently stacks up to 3 high in Y.
                val existing = world.getBlock(pos.x, pos.y, pos.z)
                if (existing != BlockType.AIR && existing != blockType) {
                    return PlaceResult(
                        pos,
                        emptyList(),
                        emptyList(),
                        "XZ+Y-frac pos=$pos has different block $existing")
                }
                if ((slotsX > 1 && xOffset >= slotsX) || (slotsZ > 1 && zOffset >= slotsZ)) {
                    return PlaceResult(
                        pos,
                        emptyList(),
                        emptyList(),
                        "XZ+Y-frac offset $xOffset,$zOffset out of slots $slotsX,$slotsZ")
                }
                val usedYInSlot =
                    world.getFractionalYOffsetsAt(pos.x, pos.y, pos.z, slotKeyX, slotKeyZ)
                val nextY = (0 until maxYSlots).firstOrNull { it !in usedYInSlot }
                if (nextY == null) {
                    return PlaceResult(
                        pos,
                        emptyList(),
                        emptyList(),
                        "XZ+Y-frac slot $slotKeyX,$slotKeyZ stack full")
                }
                if (sizeX > 1 || sizeZ > 1) {
                    for (dx in 0 until sizeX) for (dz in 0 until sizeZ) {
                        if (dx == 0 && dz == 0) continue
                        val cx = pos.x + dx
                        val cz = pos.z + dz
                        val satBlock = world.getBlock(cx, pos.y, cz)
                        if (satBlock != BlockType.AIR && satBlock != blockType) {
                            return PlaceResult(
                                pos,
                                emptyList(),
                                emptyList(),
                                "XZ+Y-frac satellite $cx,${pos.y},$cz has block $satBlock")
                        }
                    }
                }
                val changes = mutableListOf<BlockChange>()
                val isFirstAtCell = existing == BlockType.AIR
                if (isFirstAtCell) {
                    val masterChange = BlockChange(pos, blockType, state, extraState)
                    world.applyChange(masterChange)
                    changes.add(masterChange)
                    if (sizeX > 1 || sizeZ > 1) {
                        for (dx in 0 until sizeX) for (dz in 0 until sizeZ) {
                            if (dx == 0 && dz == 0) continue
                            val satChange =
                                BlockChange(
                                    BlockPos(pos.x + dx, pos.y, pos.z + dz),
                                    blockType,
                                    state,
                                    extraState)
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
                        yOffset = nextY,
                        xOffset = slotKeyX,
                        zOffset = slotKeyZ,
                        colorIndex = colorIndex,
                    )
                world.applyEntityAdd(proto)
                return PlaceResult(pos, changes, listOf(proto))
            } else if (isXZFractional) {
                // XZ-fractional block (e.g. arch): multiple fit per voxel along X or Z axis
                val existing = world.getBlock(pos.x, pos.y, pos.z)
                // Master cell must be empty or already occupied by the same block type
                if (existing != BlockType.AIR && existing != blockType) {
                    return PlaceResult(
                        pos,
                        emptyList(),
                        emptyList(),
                        "XZ-frac pos=$pos has different block $existing")
                }
                val usedSlots = world.getXZOffsetsAt(pos.x, pos.y, pos.z)
                // For sub-voxel axes (slots > 1), validate slot index; for long axes (slots == 1),
                // xOffset/zOffset is a stud-position (0 = flush, 1 = +0.5 block) — range check
                // skipped
                if ((slotsX > 1 && xOffset >= slotsX) || (slotsZ > 1 && zOffset >= slotsZ)) {
                    return PlaceResult(
                        pos,
                        emptyList(),
                        emptyList(),
                        "XZ-frac offset $xOffset,$zOffset out of slots $slotsX,$slotsZ")
                }
                val slotOccupied =
                    usedSlots.any { (storedX, storedZ) ->
                        (if (slotsX > 1) storedX else 0) == slotKeyX &&
                            (if (slotsZ > 1) storedZ else 0) == slotKeyZ
                    }
                if (slotOccupied) {
                    return PlaceResult(
                        pos,
                        emptyList(),
                        emptyList(),
                        "XZ-frac slot $slotKeyX,$slotKeyZ already occupied")
                }
                // Validate satellite cells (full cells spanning integer multiples)
                if (sizeX > 1 || sizeZ > 1) {
                    for (dx in 0 until sizeX) for (dz in 0 until sizeZ) {
                        if (dx == 0 && dz == 0) continue
                        val cx = pos.x + dx
                        val cz = pos.z + dz
                        val satBlock = world.getBlock(cx, pos.y, cz)
                        if (satBlock != BlockType.AIR && satBlock != blockType) {
                            return PlaceResult(
                                pos,
                                emptyList(),
                                emptyList(),
                                "XZ-frac satellite $cx,${pos.y},$cz has block $satBlock")
                        }
                    }
                }
                val changes = mutableListOf<BlockChange>()
                val isFirstAtCell = existing == BlockType.AIR
                if (isFirstAtCell) {
                    val masterChange = BlockChange(pos, blockType, state, extraState)
                    world.applyChange(masterChange)
                    changes.add(masterChange)
                    if (sizeX > 1 || sizeZ > 1) {
                        for (dx in 0 until sizeX) for (dz in 0 until sizeZ) {
                            if (dx == 0 && dz == 0) continue
                            val satChange =
                                BlockChange(
                                    BlockPos(pos.x + dx, pos.y, pos.z + dz),
                                    blockType,
                                    state,
                                    extraState)
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
                return PlaceResult(pos, changes, listOf(proto))
            } else if (isFractional) {
                // Fractional block (plate): stacking allowed — cell may already contain a plate
                val existing = world.getBlock(pos.x, pos.y, pos.z)
                if (existing != BlockType.AIR && BlockRegistry.get(existing).brickSize[1] >= 2.0f) {
                    return PlaceResult(
                        pos, emptyList(), emptyList(), "pos=$pos has solid non-fractional block")
                }
                val usedOffsets = world.getFractionalYOffsetsAt(pos.x, pos.y, pos.z)
                // If hasEntityAt is true but no fractional masters → non-plate entity/satellite
                // present
                if (world.hasEntityAt(pos.x, pos.y, pos.z) && usedOffsets.isEmpty()) {
                    return PlaceResult(
                        pos, emptyList(), emptyList(), "pos=$pos has non-plate entity")
                }
                val nextOffset = (0 until maxYSlots).firstOrNull { it !in usedOffsets }
                if (nextOffset == null) {
                    return PlaceResult(pos, emptyList(), emptyList(), "pos=$pos plate cell full")
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
                            BlockRegistry.get(satBlock).brickSize[1] >= 2.0f) {
                            return PlaceResult(
                                pos,
                                emptyList(),
                                emptyList(),
                                "plate satellite $cx,${pos.y},$cz has solid block")
                        }
                        // Reject only if entity at satellite belongs to a DIFFERENT master
                        val satMasterPos = world.getEntityMasterWorldPos(cx, pos.y, cz)
                        if (satMasterPos != null && satMasterPos != pos) {
                            return PlaceResult(
                                pos,
                                emptyList(),
                                emptyList(),
                                "plate satellite $cx,${pos.y},$cz has entity from different master")
                        }
                        // If satMasterPos == pos: satellite of our plate at a previous yOffset →
                        // nextOffset not in usedOffsets (already validated above for master cell)
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
                    val change = BlockChange(pos, blockType, state, extraState)
                    world.applyChange(change)
                    changes.add(change)
                }
                world.applyEntityAdd(proto)
                return PlaceResult(pos, changes, listOf(proto))
            } else {
                val existing = world.getBlock(pos.x, pos.y, pos.z)
                if (!existing.isReplaceable) {
                    return PlaceResult(pos, emptyList(), emptyList(), "pos=$pos is $existing")
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
                            return PlaceResult(
                                pos,
                                emptyList(),
                                emptyList(),
                                "multi-cell pos=$cx,$cy,$cz occupied by $cellBlock")
                        }
                        if (world.hasEntityAt(cx, cy, cz)) {
                            return PlaceResult(
                                pos,
                                emptyList(),
                                emptyList(),
                                "multi-cell pos=$cx,$cy,$cz has entity")
                        }
                    }
                }
                if (world.hasEntityAt(pos.x, pos.y, pos.z)) {
                    return PlaceResult(pos, emptyList(), emptyList(), "pos=$pos has entity")
                }

                val changes = mutableListOf<BlockChange>()
                val masterChange = BlockChange(pos, blockType, state, extraState)
                world.applyChange(masterChange)
                changes.add(masterChange)

                if (isMultiCell) {
                    // Write block type to satellite cells so raycast can detect them
                    for (dx in 0 until sizeX) for (dy in 0 until sizeY) for (dz in 0 until sizeZ) {
                        if (dx == 0 && dy == 0 && dz == 0) continue
                        val satChange =
                            BlockChange(
                                BlockPos(pos.x + dx, pos.y + dy, pos.z + dz),
                                blockType,
                                state,
                                extraState)
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
                    return PlaceResult(pos, changes, listOf(proto))
                }
                return PlaceResult(pos, changes, emptyList())
            }
        }
    }
}
