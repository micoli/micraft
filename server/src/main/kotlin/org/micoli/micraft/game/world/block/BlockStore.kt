package org.micoli.micraft.game.world.block

import org.micoli.micraft.game.world.BlockEntity
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.BlockEntityProto
import org.micoli.micraft.protocol.EntityRemoveAt

/**
 * The subset of [org.micoli.micraft.game.world.WorldState] that [BlockPlacer.placeAt] and
 * [BlockBreaker.breakAt] actually need — a bounded block+entity buffer supporting block read/write
 * and fractional-entity resolution. Extracted so the same placement/removal logic can target either
 * the persistent world ([org.micoli.micraft.game.world.WorldState]) or a standalone admin
 * [org.micoli.micraft.game.world.scene.Scene] buffer (via `ScenePlacementTarget`), without
 * duplicating ~800 lines of lego/fractional-block resolution logic.
 */
interface BlockStore {
    fun getBlock(wx: Int, wy: Int, wz: Int): BlockType

    fun getState(wx: Int, wy: Int, wz: Int): Byte

    fun hasEntityAt(wx: Int, wy: Int, wz: Int): Boolean

    fun applyChange(change: BlockChange)

    fun applyEntityAdd(proto: BlockEntityProto)

    fun applyEntityRemove(worldMasterPos: BlockPos)

    fun applyEntityRemoveAt(spec: EntityRemoveAt)

    /** Returns (xOffset, zOffset) pairs of all XZ-fractional entities at this world position. */
    fun getXZOffsetsAt(wx: Int, wy: Int, wz: Int): List<Pair<Int, Int>>

    /**
     * True if this cell or one of its 4 orthogonal XZ neighbors (same Y) already hosts an
     * XZ-fractional entity.
     */
    fun hasMisalignedNeighbor(wx: Int, wy: Int, wz: Int): Boolean

    /** Returns the last-placed XZ-fractional entity at this world position. */
    fun getLastXZFractionalEntityAt(wx: Int, wy: Int, wz: Int): BlockEntity?

    /**
     * Returns yOffsets of fractional entities whose master is at this world position, restricted to
     * the given XZ sub-slot (defaults to 0,0).
     */
    fun getFractionalYOffsetsAt(
        wx: Int,
        wy: Int,
        wz: Int,
        xOffset: Int = 0,
        zOffset: Int = 0
    ): List<Int>

    /**
     * Returns the topmost (highest yOffset) fractional entity master at this world position,
     * optionally restricted to an XZ sub-slot.
     */
    fun getTopmostFractionalEntityAt(
        wx: Int,
        wy: Int,
        wz: Int,
        xOffset: Int? = null,
        zOffset: Int? = null
    ): BlockEntity?

    /** Returns world-coordinate BlockPos of master entity at given world position, or null. */
    fun getEntityMasterWorldPos(wx: Int, wy: Int, wz: Int): BlockPos?
}
