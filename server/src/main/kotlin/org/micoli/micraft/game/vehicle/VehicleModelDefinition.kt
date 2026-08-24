package org.micoli.micraft.game.vehicle

import kotlinx.serialization.Serializable

@Serializable
data class SeatOffset(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

/**
 * Optional per-model properties attached to a vehicle's bbmodel, loaded from
 * `resources/vehicles/<name>/<name>.yaml` — mirrors [org.micoli.micraft.game.skin.SkinDefinition]'s
 * role/shape for player skins.
 */
@Serializable
data class VehicleModelDefinition(
    val speed: Float = 2f,
    val seatOffset: SeatOffset = SeatOffset(),
)

/** Every field optional so a data override can change only what it needs. */
@Serializable
data class VehicleModelYamlOverride(
    val speed: Float? = null,
    val seatOffset: SeatOffset? = null,
)
