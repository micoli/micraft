package org.micoli.micraft.game.skin

import kotlinx.serialization.Serializable

/**
 * Eye anchor of a skin, in bbmodel pixel coordinates (16 px = 1 block), model space with the feet
 * at y = 0. The first-person camera sits exactly there.
 */
@Serializable
data class EyeAnchor(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

@Serializable
data class SkinDefinition(
    val eyes: EyeAnchor = EyeAnchor(),
    val firstPersonHiddenBones: List<String> = emptyList(),
)

/** Every field optional so a data override can change only what it needs. */
@Serializable
data class SkinYamlOverride(
    val eyes: EyeAnchor? = null,
    val firstPersonHiddenBones: List<String>? = null,
)
