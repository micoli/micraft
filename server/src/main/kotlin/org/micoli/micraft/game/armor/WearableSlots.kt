package org.micoli.micraft.game.armor

import kotlinx.serialization.Serializable

@Serializable
data class WearableSlots(
    val head: Boolean = false,
    val body: Boolean = false,
    val rightArm: Boolean = false,
    val leftArm: Boolean = false,
    val rightLeg: Boolean = false,
    val leftLeg: Boolean = false,
) {
    fun toSet(): Set<String> = buildSet {
        if (head) add("head")
        if (body) add("body")
        if (rightArm) add("rightArm")
        if (leftArm) add("leftArm")
        if (rightLeg) add("rightLeg")
        if (leftLeg) add("leftLeg")
    }

    fun overlaps(other: WearableSlots): Boolean = toSet().intersect(other.toSet()).isNotEmpty()
}
