package org.micoli.micraft.combat

import kotlinx.serialization.Serializable

@Serializable
data class AttackLevelDefinition(
    val power: Int = 1,
    val cooldownMs: Long = 1000,
    val manaCost: Int = 0,
    val rageCost: Int = 0,
    val rangeOverride: Float? = null,
    val weaponDice: String = "1d4",
    val statusEffect: StatusEffect? = null,
)
