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
    val vel: Vec3 = Vec3(0f, 0f, 0f),
    val currentHp: Int = 0,
    val maxHp: Int = 0,
    val aggroTargetId: String? = null,
    val level: Int = 1,
    val xp: Int = 0,
    val isDead: Boolean = false,
    val animalData: AnimalStateData? = null,
    val scale: Float = 1.0f,
    val ownerId: String? = null,
)
