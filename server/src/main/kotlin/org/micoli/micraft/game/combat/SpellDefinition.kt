package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable

enum class SpellType {
    TOKEN_RAGE_CONSUME,
}

@Serializable
data class SpellDefinition(
    val type: SpellType = SpellType.TOKEN_RAGE_CONSUME,
    val enabled: Boolean = true,
    val rageGain: Int = 20,
    val tokenCost: Int = 0,
    val manaCost: Int = 0,
    val rageCost: Int = 0,
    val cooldownMs: Long = 0L,
)
