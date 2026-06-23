package org.micoli.micraft.protocol

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.ui.GameLayout
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.ItemType

@Serializable
sealed class ClientMessage {
    @Serializable
    data class Connect(val playerName: String, val userName: String = playerName, val preferredLanguage: String = "en") : ClientMessage()

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
        val speedUp: Boolean = false,
        val speedDown: Boolean = false,
    ) : ClientMessage()

    @Serializable
    data class ChunkUnload(val positions: List<ChunkPos>) : ClientMessage()

    @Serializable
    data class BlockBreakStart(val pos: BlockPos) : ClientMessage()

    @Serializable
    object BlockBreakStop : ClientMessage()

    @Serializable
    data class Command(val text: String) : ClientMessage()

    @Serializable
    data class BlockPlace(val pos: BlockPos, val itemType: ItemType) : ClientMessage()

    @Serializable
    data class ShortcutBarSet(val slot: Int, val itemType: ItemType?) : ClientMessage()

    @Serializable
    data class LayoutUpdate(
        val layouts: List<GameLayout>,
        val activeLayout: String,
    ) : ClientMessage()

    @Serializable
    data class Disconnect(val reason: String = "") : ClientMessage()

    @Serializable
    data class NpcInteract(val npcId: String) : ClientMessage()
}
