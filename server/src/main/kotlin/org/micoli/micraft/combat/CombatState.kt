package org.micoli.micraft.combat

data class CombatState(
    val targetId: String? = null,
    val targetIsNpc: Boolean = false,
    val attackCooldownUntilMs: Long = 0L,
    val attackCooldownsUntilMs: Map<String, Long> = emptyMap(),
    val activeEffects: MutableList<ActiveStatusEffect> = mutableListOf(),
    val downingSuccesses: Int = 0,
    val downingFailures: Int = 0,
)
