package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

@Serializable
data class BlockDefinition(
    val hardness: Float = 1f,
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
    val isSlope: Boolean = false,
    val brickSize: List<Int> = listOf(1, 1, 1),
    val heightFraction: Float = 1.0f,
)
