package org.micoli.micraft.game.world.scene

import kotlin.math.floor
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.block.BlockStore
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.BlockEntityProto

/**
 * Stamps a [Scene]'s already-resolved `blocks`/`states`/`entities` buffers into a live [BlockStore]
 * (the persistent world, via [org.micoli.micraft.game.world.WorldState]) at a given origin and 90°
 * rotation step. Unlike [org.micoli.micraft.game.world.block.BlockPlacer.placeAt] — which resolves
 * a single interactive placement against collision/fractional-slot rules — this is a direct copy of
 * cells that were already resolved once when the Scene was edited, so it writes through
 * [BlockStore.applyChange]/[BlockStore.applyEntityAdd] rather than re-running placement resolution.
 */
object ScenePlacer {
    /** origin = world-space position the scene's local (0,0,0) maps to, after rotation. */
    fun stamp(scene: Scene, origin: BlockPos, rotationSteps: Int, target: BlockStore) {
        val steps = ((rotationSteps % 4) + 4) % 4
        for (idx in 0 until scene.width * scene.height * scene.depth) {
            val wireIndex = scene.blocks[idx].toInt() and 0xFF
            val type = BlockRegistry.byWireIndex(wireIndex)
            if (type == BlockType.AIR) continue
            val (lx, ly, lz) = scene.idxToXYZ(idx)
            val (rx, rz) = rotate2D(lx, lz, scene.width, scene.depth, steps)
            val worldPos = BlockPos(origin.x + rx, origin.y + ly, origin.z + rz)
            val oldState = scene.stateAt(lx, ly, lz)
            val newState =
                BlockState.pack(
                    (BlockState.rotation(oldState) + steps) % 4, BlockState.colorIndex(oldState))
            val extraState = scene.extraStateAt(lx, ly, lz)
            target.applyChange(BlockChange(worldPos, type, newState, extraState))
        }

        scene.entities.forEach { entity ->
            val (mx, my, mz) = scene.idxToXYZ(entity.masterIdx)
            val (rmx, rmz) = rotate2D(mx, mz, scene.width, scene.depth, steps)
            val (rsx, rsz) =
                if (steps % 2 == 0) entity.sizeX to entity.sizeZ else entity.sizeZ to entity.sizeX
            val (oldSlotsX, oldSlotsZ) = gridSlots(entity.type, entity.rotation)
            val (rxOffset, rzOffset) =
                rotate2D(entity.xOffset, entity.zOffset, oldSlotsX, oldSlotsZ, steps)
            target.applyEntityAdd(
                BlockEntityProto(
                    worldX = origin.x + rmx,
                    worldY = origin.y + my,
                    worldZ = origin.z + rmz,
                    type = entity.type.id,
                    sizeX = rsx,
                    sizeY = entity.sizeY,
                    sizeZ = rsz,
                    rotation = (entity.rotation + steps) % 4,
                    yOffset = entity.yOffset,
                    xOffset = rxOffset,
                    zOffset = rzOffset,
                    colorIndex = entity.colorIndex,
                ))
        }
    }

    /**
     * Rotates a 2D coordinate `steps` quarter-turns clockwise within a `dimA`×`dimB` bounding box,
     * returning coordinates within the (possibly axis-swapped) rotated box. Used both for cell
     * positions (dimA/dimB = scene width/depth) and fractional-entity grid slots (dimA/dimB =
     * gridSlotsX/gridSlotsZ), which follow the identical axis-swap-on-odd-steps rule.
     */
    private fun rotate2D(a: Int, b: Int, dimA: Int, dimB: Int, steps: Int): Pair<Int, Int> =
        when (steps and 3) {
            0 -> a to b
            1 -> (dimB - 1 - b) to a
            2 -> (dimA - 1 - a) to (dimB - 1 - b)
            3 -> b to (dimA - 1 - a)
            else -> a to b
        }

    /**
     * A copy of [Scene.blocks] with every cell covered by a fractional/lego [Scene.entities]
     * footprint additionally marked non-air (any nonzero byte — the ghost preview only checks for
     * air vs. non-air, never the actual block type). Without this, cells that only exist as an
     * entity (never written into the raw `blocks` grid — see [Scene] doc) would be invisible in the
     * creative-mode ghost preview even though [stamp] places them correctly.
     */
    fun previewOccupancy(scene: Scene): ByteArray {
        val occupancy = scene.blocks.copyOf()
        scene.entities.forEach { entity ->
            val (mx, my, mz) = scene.idxToXYZ(entity.masterIdx)
            for (dx in 0 until entity.sizeX) for (dy in 0 until entity.sizeY) for (dz in
                0 until entity.sizeZ) {
                val nx = mx + dx
                val ny = my + dy
                val nz = mz + dz
                if (!scene.contains(nx, ny, nz)) continue
                val idx = scene.idx(nx, ny, nz)
                if (occupancy[idx].toInt() == 0) occupancy[idx] = 1
            }
        }
        return occupancy
    }

    /** Mirrors BlockPlacer.placeAt's gridSlotsX/gridSlotsZ computation for a given rotation. */
    private fun gridSlots(type: BlockType, rotation: Int): Pair<Int, Int> {
        val def = BlockRegistry.get(type)
        val brickSizeX = def.brickSize.getOrElse(0) { 2f }
        val brickSizeZ = def.brickSize.getOrElse(2) { 2f }
        val effectiveSizeX = if (rotation % 2 == 0) brickSizeX else brickSizeZ
        val effectiveSizeZ = if (rotation % 2 == 0) brickSizeZ else brickSizeX
        val gridSlotsX = if (effectiveSizeX < 2.0f) floor(2.0f / effectiveSizeX).toInt() else 1
        val gridSlotsZ = if (effectiveSizeZ < 2.0f) floor(2.0f / effectiveSizeZ).toInt() else 1
        return gridSlotsX to gridSlotsZ
    }
}
