package org.micoli.micraft.game.combat

import kotlinx.serialization.Serializable

@Serializable data class SpellConfigData(val spells: Map<String, SpellDefinition> = emptyMap())
