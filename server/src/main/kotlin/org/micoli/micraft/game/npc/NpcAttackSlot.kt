package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

@Serializable
data class NpcAttackSlot(
    val attackId: String,
    val rank: Int = 1,
)
