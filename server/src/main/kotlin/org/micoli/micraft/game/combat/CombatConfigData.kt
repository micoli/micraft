package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable

@Serializable
data class CombatConfigData(
    val maxCombatRange: Float = 10.0f,
    val npcMaxAttackRange: Float = 3.0f,
    val downingRollIntervalMs: Long = 3000L,
    val maxRage: Int = 100,
)
