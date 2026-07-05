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
    data class Connect(
        val playerName: String,
        val userName: String = playerName,
        val preferredLanguage: String = "en",
        val token: String = "",
    ) : ClientMessage()

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

    @Serializable data class ChunkUnload(val positions: List<ChunkPos>) : ClientMessage()

    @Serializable data class BlockBreakStart(val pos: BlockPos) : ClientMessage()

    @Serializable object BlockBreakStop : ClientMessage()

    @Serializable data class Command(val text: String) : ClientMessage()

    @Serializable
    data class BlockPlace(val pos: BlockPos, val itemType: ItemType) : ClientMessage()

    @Serializable
    data class ShortcutBarSet(val slot: Int, val itemType: ItemType?) : ClientMessage()

    @Serializable
    data class LayoutUpdate(
        val layouts: List<GameLayout>,
        val activeLayout: String,
    ) : ClientMessage()

    @Serializable data class Disconnect(val reason: String = "") : ClientMessage()

    @Serializable data class NpcInteract(val npcId: String) : ClientMessage()

    @Serializable data class ChatSend(val channel: String, val text: String) : ClientMessage()

    @Serializable
    data class PreferencesUpdate(
        val subscribedChannels: List<String>,
        val disabledCommands: Set<String>,
        val shadersEnabled: Boolean,
        val keybindings: Map<String, List<String>> = emptyMap(),
        val customCommands: Map<String, List<String>> = emptyMap(),
        val animatedFavicon: Boolean = true,
        val chunkDebugVisible: Boolean = false,
    ) : ClientMessage()

    @Serializable data class ViewModeUpdate(val viewMode: String) : ClientMessage()

    @Serializable data class DoCraft(val recipeId: String, val count: Int) : ClientMessage()

    @Serializable
    data class SetCombatTarget(val targetId: String?, val isNpc: Boolean) : ClientMessage()

    @Serializable
    data class AttackTarget(
        val targetId: String,
        val isNpc: Boolean,
        val attackId: String,
    ) : ClientMessage()
}
