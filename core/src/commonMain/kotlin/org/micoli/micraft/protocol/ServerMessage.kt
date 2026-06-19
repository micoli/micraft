package org.micoli.micraft.protocol

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk

@Serializable
sealed class ServerMessage {
    @Serializable
    data class Welcome(val playerId: String, val spawnPos: Vec3) : ServerMessage()

    @Serializable
    data class ChunkData(val chunk: Chunk) : ServerMessage()

    @Serializable
    data class PlayerUpdate(val state: PlayerState) : ServerMessage()

    @Serializable
    data class WorldUpdate(val changes: List<BlockChange>) : ServerMessage()

    @Serializable
    data class PlayerLeft(val playerId: String) : ServerMessage()
}

@Serializable
data class BlockChange(val pos: BlockPos, val type: BlockType)
