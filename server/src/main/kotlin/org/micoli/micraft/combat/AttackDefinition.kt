package org.micoli.micraft.combat

import kotlinx.serialization.Serializable

@Serializable
enum class DamageType {
    PHYSICAL,
    FIRE,
    POISON,
    MAGIC,
    LIGHTNING,
    NECROTIC
}

@Serializable
data class AttackDefinition(
    val damageType: DamageType = DamageType.PHYSICAL,
    val levels: Map<Int, AttackLevelDefinition> = emptyMap(),
)
