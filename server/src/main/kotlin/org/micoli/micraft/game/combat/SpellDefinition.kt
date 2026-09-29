package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.world.AbilityRank
import org.micoli.micraft.schema.JsonSchemaRoot

enum class SpellType {
    TOKEN_RAGE_CONSUME,
    NECROTIC_AOE,
    /** Single-target, guaranteed-hit damage — no to-hit roll, no armor mitigation. */
    DIRECT_DAMAGE,
    /** Self-targeted; grants a timed [StatusEffect.Protected] with the Rank's defense bonuses. */
    PROTECTION,
}

@Serializable
@JsonSchemaRoot(file = "skill-spell.schema.json")
data class SpellDefinition(
    val type: SpellType = SpellType.TOKEN_RAGE_CONSUME,
    val enabled: Boolean = true,
    val ranks: Map<Int, SpellRankDefinition> = emptyMap(),
) {
    /** The Rank a caster of [level] uses, null when this Spell only defines higher Ranks. */
    fun usableRank(level: Int): Int? = AbilityRank.usable(ranks.keys, level)
}

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
