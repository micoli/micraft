package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable
import org.micoli.micraft.combat.AttackDefinition

@Serializable data class AttackConfigData(val attacks: Map<String, AttackDefinition> = emptyMap())
