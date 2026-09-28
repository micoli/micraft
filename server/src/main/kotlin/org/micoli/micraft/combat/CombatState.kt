package org.micoli.micraft.combat

data class CombatState(
    val targetId: String? = null,
    val targetIsNpc: Boolean = false,
    val globalCooldownUntilMs: Long = 0L,
    val activeEffects: MutableList<ActiveStatusEffect> = mutableListOf(),
    val downingSuccesses: Int = 0,
    val downingFailures: Int = 0,
)
