package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.rail.RailDefinition

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
    /** Rail-network declaration — see [RailDefinition]. `null` for a non-rail block. */
    val rail: RailDefinition? = null,
    /** Weapon/tool category required to break this block. `null` = breakable bare-handed. */
    val requiredEquipment: EquipmentCategory? = null,
)
