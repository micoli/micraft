package org.micoli.micraft.npc

import kotlinx.serialization.Serializable

@Serializable
data class NpcStatBlock(
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
)
