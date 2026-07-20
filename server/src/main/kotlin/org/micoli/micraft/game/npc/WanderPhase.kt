package org.micoli.micraft.game.npc

sealed class WanderPhase {
    data class Moving(
        val targetX: Float,
        val targetZ: Float,
        val speedMult: Float,
        val remainingTicks: Int,
    ) : WanderPhase()

    data class Decel(
        val targetX: Float,
        val targetZ: Float,
        val remainingTicks: Int,
        val speedMult: Float,
    ) : WanderPhase()

    data class Pausing(
        val remainingTicks: Int,
        val lookYaw: Float,
        val lookChangeTicks: Int,
    ) : WanderPhase()
}
