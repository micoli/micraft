package org.micoli.micraft.placeable.siege

import kotlinx.serialization.Serializable

/**
 * Network-facing snapshot of a spawned siege weapon's siege-specific state — position/orientation
 * live on the linked [org.micoli.micraft.placeable.PlaceableState] (see [placeableId]), never
 * duplicated here.
 */
@Serializable
data class SiegeWeaponState(
    val id: String,
    val placeableId: String,
    val pitchStep: Int = 0,
    val powerStep: Int = 0,
    val cooldownUntilMs: Long = 0,
)
