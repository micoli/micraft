package org.micoli.micraft.tick

import org.micoli.micraft.player.PlayerStance

data class TickInput(
    val dx: Float,
    val dz: Float,
    val dy: Float,
    val yaw: Float,
    val pitch: Float,
    val stance: PlayerStance,
    val jumpRequested: Boolean,
    val flyToggleRequested: Boolean,
    val speedUpRequested: Boolean,
    val speedDownRequested: Boolean,
)
