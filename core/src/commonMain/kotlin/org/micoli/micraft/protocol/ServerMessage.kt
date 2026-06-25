package org.micoli.micraft.protocol

import kotlinx.serialization.Serializable
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.ui.GameLayout
import org.micoli.micraft.ui.defaultLayout
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.ChunkPos
import org.micoli.micraft.world.ItemType
import org.micoli.micraft.world.WorldItem
import org.micoli.micraft.world.proceduralGenerator.weather.WeatherZoneInfo

@Serializable
sealed class ServerMessage {
    @Serializable
    data class Welcome(
        val playerId: String,
        val playerName: String,
        val spawnPos: Vec3,
        val language: String = "en",
        val shadersEnabled: Boolean = true,
        val layouts: List<GameLayout> = listOf(defaultLayout()),
        val activeLayout: String = "default",
    ) : ServerMessage()

    @Serializable data class ShadersUpdate(val enabled: Boolean) : ServerMessage()

    @Serializable
    data class ChunkData(
        val pos: ChunkPos,
        val topY: Int,
        val wireBlocks: ByteArray,
    ) : ServerMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ChunkData

            if (topY != other.topY) return false
            if (pos != other.pos) return false
            if (!wireBlocks.contentEquals(other.wireBlocks)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = topY
            result = 31 * result + pos.hashCode()
            result = 31 * result + wireBlocks.contentHashCode()
            return result
        }
    }

    @Serializable data class PlayerUpdate(val state: PlayerState) : ServerMessage()

    @Serializable data class WorldUpdate(val changes: List<BlockChange>) : ServerMessage()

    @Serializable data class PlayerLeft(val playerId: String) : ServerMessage()

    @Serializable
    data class BlockBreakProgress(val pos: BlockPos, val progress: Int, val hardness: Int) :
        ServerMessage()

    @Serializable
    data class Notification(val message: String, val channel: String = "system") : ServerMessage()

    @Serializable
    data class ChatMessage(val channel: String, val sender: String, val message: String) :
        ServerMessage()

    @Serializable
    data class ChannelsSync(val subscribedChannels: List<String>, val knownChannels: List<String>) :
        ServerMessage()

    @Serializable data class ItemsSpawned(val items: List<WorldItem>) : ServerMessage()

    @Serializable data class ItemDespawned(val id: String) : ServerMessage()

    @Serializable data class InventoryUpdate(val inventory: Map<ItemType, Int>) : ServerMessage()

    @Serializable data class TimeUpdate(val gameTicks: Long) : ServerMessage()

    @Serializable data class ShortcutBarUpdate(val slots: List<ItemType?>) : ServerMessage()

    @Serializable
    data class LayoutsSync(val layouts: List<GameLayout>, val activeLayout: String) :
        ServerMessage()

    @Serializable object OpenLayoutEditor : ServerMessage()

    @Serializable object OpenPreferences : ServerMessage()

    @Serializable
    data class RegistrySync(
        val blocks: List<BlockInfo>,
        val items: Map<String, ItemInfo>,
        val npcs: Map<String, String> = emptyMap(),
    ) : ServerMessage()

    @Serializable data class NpcSpawned(val npc: NpcState) : ServerMessage()

    @Serializable data class NpcDespawned(val id: String) : ServerMessage()

    @Serializable data class NpcUpdate(val npc: NpcState) : ServerMessage()

    @Serializable
    data class NpcInteractResult(val npcId: String, val payload: String) : ServerMessage()

    @Serializable
    data class PreferencesSync(
        val subscribedChannels: List<String>,
        val knownChannels: List<String>,
        val disabledCommands: Set<String>,
        val shadersEnabled: Boolean,
        val commands: List<CommandInfo>,
    ) : ServerMessage()

    @Serializable data class WeatherUpdate(val zones: List<WeatherZoneInfo>) : ServerMessage()
}

@Serializable
data class BlockInfo(
    val name: String,
    val hardness: Int,
    val solid: Boolean,
    val transparent: Boolean,
    val minimapColor: List<Int>,
    val modelElement: String,
)

@Serializable
data class ItemInfo(
    val buildable: Boolean,
    val placesBlock: String? = null,
)

@Serializable data class BlockChange(val pos: BlockPos, val type: BlockType)

@Serializable
data class CommandInfo(
    val id: String,
    val command: String,
    val description: String,
    val autocompleteArgs: List<Int> = emptyList()
)
