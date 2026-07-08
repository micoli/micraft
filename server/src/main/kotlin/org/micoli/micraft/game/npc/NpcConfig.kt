package org.micoli.micraft.game.npc

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NpcConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val wanderPauseTicksMin: Int = 40,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val wanderPauseTicksMax: Int = 120,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val wanderStepTicksMax: Int = 60,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val interactionRange: Float = 4f,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val updateRange: Float = 96f,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val spawnCheckIntervalTicks: Int = 200,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val maxSpawnAttemptsPerTick: Int = 3,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val jumpVelocity: Float = 6.5f,
)
