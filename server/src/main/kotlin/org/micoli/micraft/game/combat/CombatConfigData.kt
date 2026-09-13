package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.schema.JsonSchemaRoot

@Serializable
@JsonSchemaRoot(file = "combat.schema.json")
data class CombatConfigData(
    val maxCombatRange: Float = 10.0f,
    val npcMaxAttackRange: Float = 3.0f,
    val downingRollIntervalMs: Long = 3000L,
    val maxRage: Int = 100,
    /**
     * Shared cooldown applied to every attack and spell cast, on top of that ability's own
     * (typically longer) cooldown — the client's GcdBar assumes this exact value.
     */
    val globalCooldownMs: Long = 1500L,
)
