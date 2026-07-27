package org.micoli.micraft.combat

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = StatusEffectSerializer::class)
sealed class StatusEffect {
    abstract val durationSec: Float
    abstract val damage: Float
    abstract val damageEffectName: String?

    data object Poisoned : StatusEffect() {
        override val durationSec = 10f
        override val damage = 2f
        override val damageEffectName = "poison"
    }

    data object Burning : StatusEffect() {
        override val durationSec = 5f
        override val damage = 3f
        override val damageEffectName = "burn"
    }

    data object Paralyzed : StatusEffect() {
        override val durationSec = 2f
        override val damage = 0f
        override val damageEffectName = null
    }

    data object Stunned : StatusEffect() {
        override val durationSec = 3f
        override val damage = 0f
        override val damageEffectName = null
    }

    data object Blessed : StatusEffect() {
        override val durationSec = 30f
        override val damage = 0f
        override val damageEffectName = null
    }

    data object Cursed : StatusEffect() {
        override val durationSec = 60f
        override val damage = 0f
        override val damageEffectName = null
    }

    data object Frozen : StatusEffect() {
        override val durationSec = 5f
        override val damage = 0f
        override val damageEffectName = null
    }

    data object FrozenInTime : StatusEffect() {
        override val durationSec = 4f
        override val damage = 0f
        override val damageEffectName = null
    }

    data object Pyre : StatusEffect() {
        override val durationSec = 8f
        override val damage = 4f
        override val damageEffectName = "pyre"
    }

    data object Withering : StatusEffect() {
        override val durationSec = 8f
        override val damage = 4f
        override val damageEffectName = "wither"
    }

    data object HpBoost : StatusEffect() {
        override val durationSec = 60f
        override val damage = 0f
        override val damageEffectName = null
    }

    data object ManaBoost : StatusEffect() {
        override val durationSec = 60f
        override val damage = 0f
        override val damageEffectName = null
    }

    data object HpRegenBoost : StatusEffect() {
        override val durationSec = 60f
        override val damage = 0f
        override val damageEffectName = null
    }

    data object ManaRegenBoost : StatusEffect() {
        override val durationSec = 60f
        override val damage = 0f
        override val damageEffectName = null
    }
}

object StatusEffectSerializer : KSerializer<StatusEffect> {
    override val descriptor = PrimitiveSerialDescriptor("StatusEffect", PrimitiveKind.STRING)

    private val byName: Map<String, StatusEffect> =
        listOf(
                StatusEffect.Poisoned,
                StatusEffect.Burning,
                StatusEffect.Paralyzed,
                StatusEffect.Stunned,
                StatusEffect.Blessed,
                StatusEffect.Cursed,
                StatusEffect.Frozen,
                StatusEffect.FrozenInTime,
                StatusEffect.Pyre,
                StatusEffect.Withering,
                StatusEffect.HpBoost,
                StatusEffect.ManaBoost,
                StatusEffect.HpRegenBoost,
                StatusEffect.ManaRegenBoost,
            )
            .associateBy { it::class.simpleName!! }

    override fun serialize(encoder: Encoder, value: StatusEffect) {
        encoder.encodeString(value::class.simpleName!!)
    }

    override fun deserialize(decoder: Decoder): StatusEffect {
        val name = decoder.decodeString()
        return byName[name] ?: throw IllegalArgumentException("Unknown StatusEffect: $name")
    }
}

@Serializable
data class ActiveStatusEffect(
    val effect: StatusEffect,
    val expiresAtMs: Long,
)
