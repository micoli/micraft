package org.micoli.micraft.combat

import kotlinx.serialization.Serializable
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
    val levels: Map<Int, AttackLevelDefinition> = emptyMap(),
)
