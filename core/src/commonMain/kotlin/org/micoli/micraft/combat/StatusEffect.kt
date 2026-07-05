package org.micoli.micraft.combat

import kotlinx.serialization.Serializable

@Serializable
sealed class StatusEffect {
    abstract val durationSec: Float

    @Serializable
    data object Poisoned : StatusEffect() {
        override val durationSec = 10f
    }

    @Serializable
    data object Burning : StatusEffect() {
        override val durationSec = 5f
    }

    @Serializable
    data object Paralyzed : StatusEffect() {
        override val durationSec = 2f
    }

    @Serializable
    data object Stunned : StatusEffect() {
        override val durationSec = 3f
    }

    @Serializable
    data object Blessed : StatusEffect() {
        override val durationSec = 30f
    }

    @Serializable
    data object Cursed : StatusEffect() {
        override val durationSec = 60f
    }
}

@Serializable
data class ActiveStatusEffect(
    val effect: StatusEffect,
    val expiresAtMs: Long,
)
