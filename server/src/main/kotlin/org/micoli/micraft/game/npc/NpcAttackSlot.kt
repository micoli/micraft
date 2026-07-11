package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

@Serializable
data class NpcAttackSlot(
    val attackId: String,
    val level: Int = 1,
)
