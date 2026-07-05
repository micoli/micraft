package org.micoli.micraft.npc

data class NpcSpawnConfig(
    val autoSpawn: Boolean = false,
    val maxTotal: Int = 0,
    val maxPerChunk: Int = 1,
    val spawnBiomes: List<String> = emptyList(),
)

data class NpcDefinition(
    val type: String,
    val behavior: NpcBehavior,
    val behaviorKey: String = "static",
    val bbmodelFile: String,
    val width: Float,
    val height: Float,
    val wanderSpeed: Float,
    val wanderRadius: Float,
    val spawn: NpcSpawnConfig = NpcSpawnConfig(),
    val hp: Int = 20,
    val aggroMode: AggroMode = AggroMode.PASSIVE,
    val aggroRange: Float = 12.0f,
    val deaggroTimeSec: Float = 10.0f,
    val attackId: String? = null,
)
