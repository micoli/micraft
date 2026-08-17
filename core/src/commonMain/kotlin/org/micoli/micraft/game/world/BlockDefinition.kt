package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.rail.RailConnectionPoint

@Serializable
data class BlockDefinition(
    val hardness: Float = 1f,
    /**
     * Physics/collision only: true if entities cannot walk through this block. See [isCubic] for
     * rendering/culling shape.
     */
    val solid: Boolean = true,
    val transparent: Boolean = false,
    val minimapColor: List<Int> = listOf(128, 128, 128),
    /**
     * Mean RGB of the block's top-face texture — precomputed server-side, see
     * BlockFaceColorSampler.
     */
    val topColor: List<Int> = minimapColor,
    /**
     * Mean RGB of the block's side-face texture — precomputed server-side, see
     * BlockFaceColorSampler.
     */
    val sideColor: List<Int> = minimapColor,
    val modelElement: String = "",
    val gltfModel: String = "",
    val liquid: Boolean = false,
    val viscosity: Int = 0,
    val replaceable: Boolean = false,
    val vegetationHost: Boolean = false,
    val treeAllowed: Boolean = true,
    val minimapVisible: Boolean = true,
    val rotatable: Boolean = false,
    val hasStuds: Boolean = false,
    /**
     * Brick footprint in half-voxel units: `2f` = 1 full voxel, `1f` = 1/2 voxel, `0.5f` = 1/4
     * voxel. Always 3 elements (X, Y, Z). Y also drives sub-voxel Y-stacking (e.g. a plate with
     * brickSize[1]=0.5 stacks 4-high within one voxel) — there is no separate height field.
     */
    val brickSize: List<Float> = listOf(2f, 2f, 2f),
    /** When true, the block can be placed in any palette color (see [PlainColorRegistry]). */
    val plainColorable: Boolean = false,
    /**
     * True if this block is a full unit cube. Non-cube blocks (arches, slopes, corners, steps) must
     * set this false so neighbor faces aren't culled and greedy-merge doesn't assume full coverage.
     */
    val isCubic: Boolean = true,
    /**
     * Track-network connection groups, in the block's own unrotated (rotation=0) frame — each inner
     * list is one traversable pair (or, for a junction, one candidate pair sharing a common entry
     * point), each point's direction plus the connected neighbor's Y offset
     * ([RailConnectionPoint.dy], `0` unless declared otherwise — see there for the slope case).
     * Empty for non-rail blocks. What the groups mean depends on how many there are and whether
     * they share a direction (see [org.micoli.micraft.game.world.rail.RailConnection]):
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
)
