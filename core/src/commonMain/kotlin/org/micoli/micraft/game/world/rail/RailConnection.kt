package org.micoli.micraft.game.world.rail

import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType

/**
 * Resolves a placed block's rail connection points from its declarative
 * `BlockDefinition.connections` (unrotated frame) plus its stored rotation. Reusable by both
 * topology detection ([org.micoli.micraft.game.world.rail]) and, later, vehicle traversal.
 */
object RailConnection {
    fun isRail(blockType: BlockType): Boolean =
        BlockRegistry.get(blockType).connections.isNotEmpty()

    /** True for a switch/junction piece with more than one possible exit beyond its entry. */
    fun isJunction(blockType: BlockType): Boolean =
        BlockRegistry.get(blockType).connections.size > 2

    /** Number of switchable branches beyond the fixed entry — 0 for a non-junction piece. */
    fun branchCount(blockType: BlockType): Int =
        (BlockRegistry.get(blockType).connections.size - 1).coerceAtLeast(0)

    /** Every connection this placement exposes (rotated), regardless of switch state. */
    fun all(blockType: BlockType, state: Byte): Set<Direction> {
        val def = BlockRegistry.get(blockType)
        if (def.connections.isEmpty()) return emptySet()
        val rotation = BlockState.rotation(state)
        return def.connections.mapTo(linkedSetOf()) { it.rotatedBy(rotation) }
    }

    /**
     * Connections usable for a single straight-line pass right now: the first declared connection
     * (fixed entry) plus, when the piece has extra branch options beyond it (a switch), only the
     * one selected by the block's extra-state byte ([BlockState.extra]) as an index into the
     * remaining branches. A plain 2-ended piece (segment/curve/slope) has no branch choice — both
     * its connections are always active. Both branches of a switch still appear in [all] — this is
     * only the pruning that keeps the rail network a simple chain/cycle for topology walking and
     * vehicle traversal.
     */
    fun active(blockType: BlockType, state: Byte, extraState: Byte): Set<Direction> {
        val def = BlockRegistry.get(blockType)
        if (def.connections.isEmpty()) return emptySet()
        val rotation = BlockState.rotation(state)
        val rotated = def.connections.map { it.rotatedBy(rotation) }
        if (rotated.size <= 2) return rotated.toSet()
        val branches = rotated.drop(1)
        val branchIndex = BlockState.extra(extraState).coerceIn(0, branches.size - 1)
        return setOf(rotated[0], branches[branchIndex])
    }
}
