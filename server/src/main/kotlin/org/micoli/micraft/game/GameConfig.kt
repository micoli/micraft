package org.micoli.micraft.game

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GameConfig(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val tickMs: Long = 50L,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val gravity: Float = -20f,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val jumpSpeed: Float = 8.5f,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val flyVerticalSpeed: Float = 8f,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val saveIntervalSeconds: Int = 30,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val spawnX: Float = 8f,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val spawnY: Float = 200f,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val spawnZ: Float = 8f,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val ticksPerDay: Long = 72_000L,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val timeBroadcastTicks: Int = 20,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val maxInteractionDistance: Double = 7.0,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val debugWorld: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val reconcileToleranceXz: Double = 0.5,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val reconcileToleranceY: Double = 0.99,
)
