package org.micoli.micraft.npc

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.Vec3

@Serializable
data class NpcState(
    val id: String,
    val name: String,
    val type: String,
    val pos: Vec3,
    val yaw: Float,
    val currentHp: Int = 0,
    val maxHp: Int = 0,
)
