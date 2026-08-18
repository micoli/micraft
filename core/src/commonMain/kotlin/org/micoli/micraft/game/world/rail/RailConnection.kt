package org.micoli.micraft.game.world.rail

import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType

/**
 * Resolves a placed block's rail connection groups from its declarative
 * `BlockDefinition.rail.connections` (unrotated frame) plus its stored rotation. Reusable by both
 * topology detection ([org.micoli.micraft.game.world.rail]) and vehicle traversal.
 */
object RailConnection {
    fun isRail(blockType: BlockType): Boolean = BlockRegistry.get(blockType).rail != null

    /**
     * Voxel height a vehicle rides at above this rail block's floor — see [RailDefinition.height].
     */
    fun railHeight(blockType: BlockType): Float = BlockRegistry.get(blockType).rail?.height ?: 1f

    private fun rotatedGroups(blockType: BlockType, state: Byte): List<List<RailConnectionPoint>> {
        val rotation = BlockState.rotation(state)
        val connections = BlockRegistry.get(blockType).rail?.connections ?: emptyList()
        return connections.map { group -> group.map { it.rotatedBy(rotation) } }
    }

    /**
     * True when declared groups share no direction with each other — independent pairs, all always
     * active simultaneously (e.g. a 4-way crossing). False when groups share a direction (a
     * switch's common fixed entry) or there's only zero/one group (nothing to disambiguate).
     */
    private fun groupsAreDisjoint(groups: List<List<RailConnectionPoint>>): Boolean {
        if (groups.size <= 1) return true
        val seen = mutableSetOf<Direction>()
        for (group in groups) {
            for (point in group) {
                if (!seen.add(point.direction)) return false
            }
        }
        return true
    }

    /** True for a switch/junction piece with more than one candidate exit beyond a shared entry. */
    fun isJunction(blockType: BlockType): Boolean {
        val groups = BlockRegistry.get(blockType).rail?.connections ?: emptyList()
        return groups.size > 1 && !groupsAreDisjoint(groups)
    }

    /** Number of switchable branches — 0 for a non-junction piece. */
    fun branchCount(blockType: BlockType): Int =
        if (isJunction(blockType)) BlockRegistry.get(blockType).rail!!.connections.size else 0

    /** Every connection group this placement exposes (rotated), regardless of switch state. */
    fun allGroups(blockType: BlockType, state: Byte): List<List<RailConnectionPoint>> =
        rotatedGroups(blockType, state)

    /** Every connection point this placement exposes (rotated), regardless of switch state. */
    fun allPoints(blockType: BlockType, state: Byte): Set<RailConnectionPoint> =
        rotatedGroups(blockType, state).flatten().toSet()

    /** Every connection direction this placement exposes (rotated), regardless of switch state. */
    fun all(blockType: BlockType, state: Byte): Set<Direction> =
        allPoints(blockType, state).mapTo(linkedSetOf()) { it.direction }

    /**
     * Connection group(s) usable for a single straight-line pass right now. A plain (1-group) piece
     * or a crossing (disjoint groups) has no branch choice — every declared group is always active.
     * A switch/junction returns only the one group selected by the block's extra-state byte
     * ([BlockState.extra]) as an index into its declared groups. [allGroups] always has every
     * candidate group regardless of switch state — this is only the pruning that keeps a switch's
     * momentary path a simple chain for topology walking and vehicle traversal.
     */
    fun activeGroups(
        blockType: BlockType,
        state: Byte,
        extraState: Byte
    ): List<List<RailConnectionPoint>> {
        val groups = rotatedGroups(blockType, state)
        if (groups.isEmpty()) return emptyList()
        if (groups.size == 1 || groupsAreDisjoint(groups)) return groups
        val index = BlockState.extra(extraState).coerceIn(0, groups.size - 1)
        return listOf(groups[index])
    }

    /** Every active connection point — see [activeGroups]. */
    fun activePoints(
        blockType: BlockType,
        state: Byte,
        extraState: Byte
    ): Set<RailConnectionPoint> = activeGroups(blockType, state, extraState).flatten().toSet()

    /** Every active connection direction — see [activeGroups]. */
    fun active(blockType: BlockType, state: Byte, extraState: Byte): Set<Direction> =
        activePoints(blockType, state, extraState).mapTo(linkedSetOf()) { it.direction }

    /**
     * Direction to continue in after arriving via [arrivalDir], given the [groups] active at the
     * cell now occupied ([activeGroups]). Finds the group [arrivalDir] belongs to and returns its
     * other member's direction — correct for a straight piece, a curve's perpendicular turn, a
     * switch's pruned branch, and each independent pair of a crossing alike, since a crossing's
     * pairs never share a direction (unlike the flattened direction set, which can't tell which
     * pair [arrivalDir] came from and would risk matching it to an unrelated pair's direction).
     * Null if [arrivalDir] isn't part of any active group (a true dead end).
     */
    fun preferredContinuation(
        groups: List<List<RailConnectionPoint>>,
        arrivalDir: Direction
    ): Direction? =
        groups
            .firstOrNull { group -> group.any { it.direction == arrivalDir } }
            ?.firstOrNull { it.direction != arrivalDir }
            ?.direction
}
