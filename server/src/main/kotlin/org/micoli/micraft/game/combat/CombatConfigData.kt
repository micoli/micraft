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
)
