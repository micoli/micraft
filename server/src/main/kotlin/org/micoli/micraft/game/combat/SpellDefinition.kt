package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.combat.StatusEffect
import org.micoli.micraft.game.classes.ClassDefinitionEntry
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

/**
 * This Class's [SpellType.PROTECTION] Spell grant(s), as (level granted, spellId) pairs sorted by
 * level ascending, deduplicated by spellId. Shared by [SpellProcessor] (which additionally filters
 * by the caster's Level) and the admin `/api/admin/protections` route (which wants the Class's
 * configured Protection regardless of Level) — kept in one place so the two never resolve different
 * spellIds for the same Class.
 */
fun ClassDefinitionEntry.protectionSpellGrants(
    spellRegistry: Map<String, SpellDefinition>
): List<Pair<Int, String>> =
    levels.entries
        .sortedBy { it.key }
        .flatMap { (level, entry) -> entry.spells.map { level to it.spell } }
        .filter { (_, spellId) -> spellRegistry[spellId]?.type == SpellType.PROTECTION }
        .distinctBy { it.second }

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
