package org.micoli.micraft.protocol

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.world.ChunkPos

@Serializable
sealed class ClientMessage {
    @Serializable
    data class Connect(val playerName: String) : ClientMessage()

    @Serializable
    data class MoveIntent(
        val dx: Float,
        val dz: Float,
        val yaw: Float,
        val pitch: Float,
        val stance: PlayerStance = PlayerStance.STANDING,
        val jump: Boolean = false,
        val dy: Float = 0f,
        val flyToggle: Boolean = false,
    ) : ClientMessage()

    @Serializable
    data class ChunkUnload(val positions: List<ChunkPos>) : ClientMessage()

    @Serializable
    data class Disconnect(val reason: String = "") : ClientMessage()
}
