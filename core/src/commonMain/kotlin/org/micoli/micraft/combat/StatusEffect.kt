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

    data object Poisoned : StatusEffect() {
        override val durationSec = 10f
    }

    data object Burning : StatusEffect() {
        override val durationSec = 5f
    }

    data object Paralyzed : StatusEffect() {
        override val durationSec = 2f
    }

    data object Stunned : StatusEffect() {
        override val durationSec = 3f
    }

    data object Blessed : StatusEffect() {
        override val durationSec = 30f
    }

    data object Cursed : StatusEffect() {
        override val durationSec = 60f
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
