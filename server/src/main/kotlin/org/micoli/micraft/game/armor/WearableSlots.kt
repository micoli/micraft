package org.micoli.micraft.game.armor

import kotlinx.serialization.Serializable

@Serializable
data class WearableSlots(
    val head: Boolean = false,
    val body: Boolean = false,
    val cape: Boolean = false,
    val rightBiceps: Boolean = false,
    val rightForearm: Boolean = false,
    val rightHand: Boolean = false,
    val leftBiceps: Boolean = false,
    val leftForearm: Boolean = false,
    val leftHand: Boolean = false,
    val rightThigh: Boolean = false,
    val rightCalf: Boolean = false,
    val rightFoot: Boolean = false,
    val leftThigh: Boolean = false,
    val leftCalf: Boolean = false,
    val leftFoot: Boolean = false,
) {
    fun toSet(): Set<String> = buildSet {
        if (head) add("head")
        if (body) add("body")
        if (cape) add("cape")
        if (rightBiceps) add("rightBiceps")
        if (rightForearm) add("rightForearm")
        if (rightHand) add("rightHand")
        if (leftBiceps) add("leftBiceps")
        if (leftForearm) add("leftForearm")
        if (leftHand) add("leftHand")
        if (rightThigh) add("rightThigh")
        if (rightCalf) add("rightCalf")
        if (rightFoot) add("rightFoot")
        if (leftThigh) add("leftThigh")
        if (leftCalf) add("leftCalf")
        if (leftFoot) add("leftFoot")
    }

    fun overlaps(other: WearableSlots): Boolean = toSet().intersect(other.toSet()).isNotEmpty()
}
