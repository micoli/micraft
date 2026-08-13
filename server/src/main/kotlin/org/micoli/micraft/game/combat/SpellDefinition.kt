package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable
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
)
