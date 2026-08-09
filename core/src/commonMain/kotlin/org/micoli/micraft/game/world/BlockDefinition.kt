package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

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
    val brickSize: List<Float> = listOf(1f, 1f, 1f),
    val heightFraction: Float = 1.0f,
    /** When true, the block can be placed in any palette color (see [PlainColorRegistry]). */
    val plainColorable: Boolean = false,
    /**
     * True if this block is a full unit cube. Non-cube blocks (arches, slopes, corners, steps) must
     * set this false so neighbor faces aren't culled and greedy-merge doesn't assume full coverage.
     */
    val isCubic: Boolean = true,
)
