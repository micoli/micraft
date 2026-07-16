package org.micoli.micraft.protocol

import kotlinx.serialization.Serializable
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.ChannelSubscription
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.ui.GameLayout

@Serializable
sealed class ClientMessage {
    @ProtoId(0)
    @Serializable
    data class Connect(
        val playerName: String,
        val userName: String = playerName,
        val preferredLanguage: String = "en",
        val token: String = "",
    ) : ClientMessage()

    @ProtoId(1)
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

    @ProtoId(2)
    @Serializable
    data class ChunkUnload(val positions: List<ChunkPos>) : ClientMessage()

    @ProtoId(3) @Serializable data class BlockBreakStart(val pos: BlockPos) : ClientMessage()

    @ProtoId(4) @Serializable object BlockBreakStop : ClientMessage()

    @ProtoId(5) @Serializable data class Command(val text: String) : ClientMessage()

    @ProtoId(6)
    @Serializable
    data class BlockPlace(val pos: BlockPos, val itemType: ItemType) : ClientMessage()

    @ProtoId(7)
    @Serializable
    data class ShortcutBarSet(val slot: Int, val content: ShortcutSlot?) : ClientMessage()

    @ProtoId(8)
    @Serializable
    data class LayoutUpdate(
        val layouts: List<GameLayout>,
        val activeLayout: String,
    ) : ClientMessage()

    @ProtoId(9) @Serializable data class Disconnect(val reason: String = "") : ClientMessage()

    @ProtoId(10) @Serializable data class NpcInteract(val npcId: String) : ClientMessage()

    @ProtoId(11)
    @Serializable
    data class ChatSend(val channel: String, val text: String) : ClientMessage()

    @ProtoId(12)
    @Serializable
    data class PreferencesUpdate(
        val subscribedChannels: List<ChannelSubscription>,
        val disabledCommands: Set<String>,
        val shadersEnabled: Boolean,
        val keybindings: Map<String, List<String>> = emptyMap(),
        val customCommands: Map<String, List<String>> = emptyMap(),
        val animatedFavicon: Boolean = false,
        val chunkDebugVisible: Boolean = false,
        val statisticsVisible: Boolean = false,
        val macros: Map<String, String> = emptyMap(),
        val fieldOfView: Int = 70,
        val dynamicFogEnabled: Boolean = true,
    ) : ClientMessage()

    @ProtoId(13) @Serializable data class ViewModeUpdate(val viewMode: String) : ClientMessage()

    @ProtoId(14)
    @Serializable
    data class DoCraft(val recipeId: String, val count: Int) : ClientMessage()

    @ProtoId(15)
    @Serializable
    data class SetCombatTarget(val targetId: String?, val isNpc: Boolean) : ClientMessage()

    @ProtoId(16)
    @Serializable
    data class AttackTarget(
        val targetId: String,
        val isNpc: Boolean,
        val attackId: String,
        val attackLevel: Int = 1,
    ) : ClientMessage()

    @ProtoId(17) @Serializable data class RunMacro(val name: String) : ClientMessage()

    @ProtoId(18) @Serializable data class RunMacroContent(val script: String) : ClientMessage()

    @ProtoId(19) @Serializable data class UseSpell(val spellId: String) : ClientMessage()
}
