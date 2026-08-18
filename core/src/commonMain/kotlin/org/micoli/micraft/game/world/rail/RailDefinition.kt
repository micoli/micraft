package org.micoli.micraft.game.world.rail

import kotlinx.serialization.Serializable

/**
 * Rail-network declaration for a block ([org.micoli.micraft.game.world.BlockDefinition.rail]) —
 * `null` for a non-rail block.
 */
@Serializable
data class RailDefinition(
    /**
     * Track-network connection groups, in the block's own unrotated (rotation=0) frame — each inner
     * list is one traversable pair (or, for a junction, one candidate pair sharing a common entry
     * point), each point's direction plus the connected neighbor's Y offset
     * ([RailConnectionPoint.dy], `1` unless declared otherwise — see there for the slope case).
     * What the groups mean depends on how many there are and whether they share a direction (see
     * [RailConnection]):
     * - 1 group: a plain through piece (straight/curve/slope) — always active. `[[NORTH, SOUTH]]`.
     * - 2+ groups sharing a direction: a switch (e.g. a Y-split) — the shared direction is the
     *   fixed entry, and only one group (selected by the block state's extra byte,
     *   [org.micoli.micraft.game.world.BlockState.extra]) is active at a time. `[[SOUTH, NORTH],
     *   [SOUTH, EAST]]`.
     * - 2+ groups sharing no direction: independent pairs, all always active simultaneously (e.g. a
     *   4-way crossing — what enters one side always exits the opposite side, never turns onto the
     *   other pair). `[[NORTH, SOUTH], [EAST, WEST]]`.
     *
     * At runtime a placement's actual connection directions are these values rotated by the block's
     * stored rotation (0..3, 90° steps) — see [org.micoli.micraft.game.world.BlockState.rotation].
     */
    val connections: List<List<RailConnectionPoint>> = emptyList(),
    /** Voxel height a vehicle rides at above this rail block's floor. */
    val height: Float = 1f,
)
