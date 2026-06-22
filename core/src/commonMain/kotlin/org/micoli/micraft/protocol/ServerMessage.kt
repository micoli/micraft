package org.micoli.micraft.protocol

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.ItemType
import org.micoli.micraft.world.WorldItem

@Serializable
sealed class ServerMessage {
    @Serializable
    data class Welcome(val playerId: String, val playerName: String, val spawnPos: Vec3, val language: String = "en", val shadersEnabled: Boolean = true) : ServerMessage()

    @Serializable
    data class ShadersUpdate(val enabled: Boolean) : ServerMessage()

    @Serializable
    data class ChunkData(
        val pos: ChunkPos,
        val topY: Int,
        val wireBlocks: ByteArray,
    ) : ServerMessage()

    @Serializable
    data class PlayerUpdate(val state: PlayerState) : ServerMessage()

    @Serializable
    data class WorldUpdate(val changes: List<BlockChange>) : ServerMessage()

    @Serializable
    data class PlayerLeft(val playerId: String) : ServerMessage()

    @Serializable
    data class BlockBreakProgress(val pos: BlockPos, val progress: Int, val hardness: Int) : ServerMessage()

    @Serializable
    data class Notification(val message: String) : ServerMessage()

    @Serializable
    data class ItemsSpawned(val items: List<WorldItem>) : ServerMessage()

    @Serializable
    data class ItemDespawned(val id: String) : ServerMessage()

    @Serializable
    data class InventoryUpdate(val inventory: Map<ItemType, Int>) : ServerMessage()
}

@Serializable
data class BlockChange(val pos: BlockPos, val type: BlockType)
