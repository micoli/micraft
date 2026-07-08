package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

@Serializable
data class NpcYamlEntry(
    val behavior: String = "static",
    val width: Float = 0.6f,
    val height: Float = 1.8f,
    val wanderSpeed: Float = 0f,
    val wanderRadius: Float = 0f,
    val spawn: NpcSpawnConfigRaw = NpcSpawnConfigRaw(),
    val hp: Int = 20,
    val aggroMode: AggroMode = AggroMode.PASSIVE,
    val aggroRange: Float = 12.0f,
    val deaggroTimeSec: Float = 10.0f,
    val attackId: String? = null,
)
