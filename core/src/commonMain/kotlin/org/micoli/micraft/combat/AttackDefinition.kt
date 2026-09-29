package org.micoli.micraft.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.game.world.AbilityRank
import org.micoli.micraft.schema.JsonSchemaRoot

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
@JsonSchemaRoot(file = "skill-attack.schema.json")
data class AttackDefinition(
    val damageType: DamageType = DamageType.PHYSICAL,
    val enabled: Boolean = true,
    val ranks: Map<Int, AttackRankDefinition> = emptyMap(),
) {
    /** The Rank an NPC of [level] uses, null when this Attack only defines higher Ranks. */
    fun usableRank(level: Int): Int? = AbilityRank.usable(ranks.keys, level)
}
