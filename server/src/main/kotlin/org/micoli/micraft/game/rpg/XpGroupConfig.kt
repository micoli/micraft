package org.micoli.micraft.game.rpg

import kotlinx.serialization.Serializable

@Serializable
data class XpGroupConfig(
    val enabled: Boolean = true,
    val bonusPerMember: Double = 0.10,
)
