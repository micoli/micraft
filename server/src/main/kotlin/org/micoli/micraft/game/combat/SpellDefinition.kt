package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.schema.JsonSchemaRoot

enum class SpellType {
    TOKEN_RAGE_CONSUME,
    NECROTIC_AOE,
}

@Serializable
@JsonSchemaRoot(file = "skill-spell.schema.json")
data class SpellDefinition(
    val type: SpellType = SpellType.TOKEN_RAGE_CONSUME,
    val enabled: Boolean = true,
    val rageGain: Int = 20,
    val tokenCost: Int = 0,
    val manaCost: Int = 0,
    val rageCost: Int = 0,
    val cooldownMs: Long = 0L,
    val aoeRadius: Float = 0f,
    val maxRange: Float = 15f,
    /**
     * [type] NECROTIC_AOE only — name of a [StatusEffect] data object (e.g. "Frozen", "Stunned"),
     * resolved by [resolveStatusEffect]; defaults to [StatusEffect.Withering] when unset or
     * unrecognized. A plain string rather than [StatusEffect] itself: that sealed class has no
     * public constructor for the generic yaml merge/write-back reflection (`mergeConfig` in
     * YamlPatchWriter.kt) to default-instantiate when the field is null, unlike a field buried
     * inside a `Map` value (e.g. `AttackLevelDefinition.statusEffect`) which that reflection never
     * walks into.
     */
    val statusEffect: String? = null,
)

fun resolveStatusEffect(name: String?): StatusEffect =
    when (name) {
        "Poisoned" -> StatusEffect.Poisoned
        "Burning" -> StatusEffect.Burning
        "Paralyzed" -> StatusEffect.Paralyzed
        "Stunned" -> StatusEffect.Stunned
        "Blessed" -> StatusEffect.Blessed
        "Cursed" -> StatusEffect.Cursed
        "Frozen" -> StatusEffect.Frozen
        "FrozenInTime" -> StatusEffect.FrozenInTime
        "Pyre" -> StatusEffect.Pyre
        "Withering" -> StatusEffect.Withering
        "Drowning" -> StatusEffect.Drowning
        else -> StatusEffect.Withering
    }
