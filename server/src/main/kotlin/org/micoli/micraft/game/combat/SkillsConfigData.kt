package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.combat.AttackDefinition

@Serializable
data class SkillsConfigData(
    val attacks: Map<String, AttackDefinition> = emptyMap(),
    val spells: Map<String, SpellDefinition> = emptyMap(),
)
