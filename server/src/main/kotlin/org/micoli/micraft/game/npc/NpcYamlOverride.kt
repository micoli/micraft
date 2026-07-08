package org.micoli.micraft.game.npc

import kotlinx.serialization.Serializable

@Serializable
data class NpcYamlOverride(
    val behavior: String? = null,
    val width: Float? = null,
    val height: Float? = null,
    val wanderSpeed: Float? = null,
    val wanderRadius: Float? = null,
    val spawn: NpcSpawnConfigRawOverride? = null,
    val hp: Int? = null,
    val aggroMode: AggroMode? = null,
    val aggroRange: Float? = null,
    val deaggroTimeSec: Float? = null,
    val attackId: String? = null,
)
