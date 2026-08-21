package org.micoli.micraft.game.equipment

import kotlinx.serialization.Serializable

/** Hand-item model rotation, in degrees, applied to the "handle" anchor when equipped. */
@Serializable data class Rotation(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)
