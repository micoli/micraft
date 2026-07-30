package org.micoli.micraft.game.world.block

import kotlinx.serialization.Serializable

@Serializable
data class BlockYamlOverride(
    val hardness: Float? = null,
    val solid: Boolean? = null,
    val transparent: Boolean? = null,
    val minimapColor: List<Int>? = null,
    val modelElement: String? = null,
    val gltfModel: String? = null,
    val liquid: Boolean? = null,
    val viscosity: Int? = null,
    val replaceable: Boolean? = null,
    val vegetationHost: Boolean? = null,
    val treeAllowed: Boolean? = null,
    val minimapVisible: Boolean? = null,
    val drops: List<DropEntry>? = null,
    val rotatable: Boolean? = null,
    val hasStuds: Boolean? = null,
    val isSlope: Boolean? = null,
    val brickSize: List<Int>? = null,
    val heightFraction: Float? = null,
)
