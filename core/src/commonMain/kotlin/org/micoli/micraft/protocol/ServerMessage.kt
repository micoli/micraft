package org.micoli.micraft.protocol

import kotlinx.serialization.Serializable
import org.micoli.micraft.combat.ActiveStatusEffect
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.game.world.RecipeDefinition
import org.micoli.micraft.game.world.WeatherZoneInfo
import org.micoli.micraft.game.world.WorldItem
import org.micoli.micraft.npc.NpcState
import org.micoli.micraft.player.ChannelSubscription
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.DerivedStats
import org.micoli.micraft.ui.GameLayout
import org.micoli.micraft.ui.defaultLayout

@Serializable
sealed class ServerMessage {
    @ProtoId(0)
    @Serializable
    data class Welcome(
        val playerId: String,
        val playerName: String,
        val spawnPos: Vec3,
        val language: String = "en",
        val shadersEnabled: Boolean = true,
        val layouts: List<GameLayout> = listOf(defaultLayout()),
        val activeLayout: String = "default",
        val viewMode: String = "FIRST_PERSON",
        val reconcileToleranceXz: Double = 0.5,
        val reconcileToleranceY: Double = 0.99,
        val chunkTransport: String = "websocket",
    ) : ServerMessage()

    @ProtoId(1) @Serializable data class ShadersUpdate(val enabled: Boolean) : ServerMessage()

    @ProtoId(2)
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

    @ProtoId(3) @Serializable data class PlayerUpdate(val state: PlayerState) : ServerMessage()

    @ProtoId(4)
    @Serializable
    data class WorldUpdate(val changes: List<BlockChange>) : ServerMessage()

    @ProtoId(5) @Serializable data class PlayerLeft(val playerId: String) : ServerMessage()

    @ProtoId(6)
    @Serializable
    data class BlockBreakProgress(val pos: BlockPos, val progress: Int, val hardness: Float) :
        ServerMessage()

    @ProtoId(7)
    @Serializable
    data class Notification(val message: String, val channel: String = "system") : ServerMessage()

    @ProtoId(8)
    @Serializable
    data class ChatMessage(val channel: String, val sender: String, val message: String) :
        ServerMessage()

    @ProtoId(9)
    @Serializable
    data class ChannelsSync(
        val subscribedChannels: List<ChannelSubscription>,
        val knownChannels: List<String>,
    ) : ServerMessage()

    @ProtoId(10)
    @Serializable
    data class ItemsSpawned(val items: List<WorldItem>) : ServerMessage()

    @ProtoId(11) @Serializable data class ItemDespawned(val id: String) : ServerMessage()

    @ProtoId(12)
    @Serializable
    data class InventoryUpdate(val inventory: Map<ItemType, Int>) : ServerMessage()

    @ProtoId(13) @Serializable data class TimeUpdate(val gameTicks: Long) : ServerMessage()

    @ProtoId(14)
    @Serializable
    data class ShortcutBarUpdate(val slots: Map<Int, ShortcutSlot> = emptyMap()) : ServerMessage()

    @ProtoId(15)
    @Serializable
    data class LayoutsSync(val layouts: List<GameLayout>, val activeLayout: String) :
        ServerMessage()

    @ProtoId(16) @Serializable object OpenLayoutEditor : ServerMessage()

    @ProtoId(17) @Serializable object OpenPreferences : ServerMessage()

    @ProtoId(18) @Serializable object OpenCodex : ServerMessage()

    @ProtoId(28) @Serializable object OpenCraft : ServerMessage()

    @ProtoId(27) @Serializable object ToggleBiomeMap : ServerMessage()

    @ProtoId(29)
    @Serializable
    data class RecipeSync(
        val recipes: Map<String, RecipeDefinition>,
        val knownRecipes: Set<String>,
    ) : ServerMessage()

    @ProtoId(19)
    @Serializable
    data class RegistrySync(
        val blocks: List<BlockInfo>,
        val items: Map<String, ItemInfo>,
        val npcs: Map<String, String> = emptyMap(),
        val npcDefinitions: Map<String, NpcCodexInfo> = emptyMap(),
    ) : ServerMessage()

    @ProtoId(20) @Serializable data class NpcSpawned(val npc: NpcState) : ServerMessage()

    @ProtoId(21) @Serializable data class NpcDespawned(val id: String) : ServerMessage()

    @ProtoId(22) @Serializable data class NpcUpdate(val npc: NpcState) : ServerMessage()

    @ProtoId(23)
    @Serializable
    data class NpcInteractResult(val npcId: String, val payload: String) : ServerMessage()

    @ProtoId(24)
    @Serializable
    data class PreferencesSync(
        val subscribedChannels: List<ChannelSubscription>,
        val knownChannels: List<String>,
        val disabledCommands: Set<String>,
        val shadersEnabled: Boolean,
        val commands: List<CommandInfo>,
        val keybindings: Map<String, List<String>> = emptyMap(),
        val customCommands: Map<String, List<String>> = emptyMap(),
        val animatedFavicon: Boolean = true,
        val chunkDebugVisible: Boolean = false,
        val macros: Map<String, String> = emptyMap(),
        val fieldOfView: Int = 70,
    ) : ServerMessage()

    @ProtoId(25)
    @Serializable
    data class WeatherUpdate(val zones: List<WeatherZoneInfo>) : ServerMessage()

    @ProtoId(26)
    @Serializable
    data class GameConfigSync(
        val reconcileToleranceXz: Double,
        val reconcileToleranceY: Double,
    ) : ServerMessage()

    @ProtoId(30)
    @Serializable
    data class OpenTrade(
        val tradeId: String,
        val otherPlayerName: String,
        val myRole: String,
    ) : ServerMessage()

    @ProtoId(31)
    @Serializable
    data class TradeUpdate(
        val tradeId: String,
        val myOffer: Map<ItemType, Int>,
        val theirOffer: Map<ItemType, Int>,
        val myAccepted: Boolean,
        val theirAccepted: Boolean,
    ) : ServerMessage()

    @ProtoId(32)
    @Serializable
    data class TradeClosed(
        val tradeId: String,
        val reason: String,
    ) : ServerMessage()

    @ProtoId(33) @Serializable object CharacterCreationRequired : ServerMessage()

    @ProtoId(34)
    @Serializable
    data class CharacterSync(
        val character: CharacterData,
        val derived: DerivedStats,
        val effectiveBaseStats: BaseStats,
    ) : ServerMessage()

    @Serializable
    data class TargetRef(
        val id: String,
        val name: String,
        val currentHp: Int,
        val maxHp: Int,
    )

    @ProtoId(35)
    @Serializable
    data class CombatTargetUpdate(
        val targetId: String?,
        val displayName: String?,
        val currentHp: Int,
        val maxHp: Int,
        val targetOfTarget: TargetRef? = null,
        val distance: Float? = null,
    ) : ServerMessage()

    @ProtoId(36)
    @Serializable
    data class HealthUpdate(
        val entityId: String,
        val isNpc: Boolean,
        val currentHp: Int,
        val maxHp: Int,
    ) : ServerMessage()

    @ProtoId(37)
    @Serializable
    data class PlayerStatusUpdate(
        val currentHp: Int,
        val maxHp: Int,
        val currentMana: Int,
        val maxMana: Int,
        val currentRage: Int,
        val maxRage: Int,
        val stance: PlayerStance,
        val globalCooldownRemainingMs: Long,
        val attackCooldownsRemainingMs: Map<String, Long> = emptyMap(),
        val currentTokens: Int = 0,
        val maxTokens: Int = 0,
    ) : ServerMessage()

    @ProtoId(38)
    @Serializable
    data class StatusEffectUpdate(
        val playerId: String,
        val effects: List<ActiveStatusEffect>,
    ) : ServerMessage()

    @ProtoId(39) @Serializable data class PlayerDowned(val playerId: String) : ServerMessage()

    @ProtoId(40)
    @Serializable
    data class PlayerRespawned(
        val playerId: String,
        val pos: Vec3,
        val currentHp: Int,
        val currentMana: Int,
    ) : ServerMessage()

    @ProtoId(41)
    @Serializable
    data class XpGained(
        val xpGained: Int,
        val totalXp: Int,
        val level: Int,
        val leveledUp: Boolean,
        val nextLevelXp: Int,
    ) : ServerMessage()
}

@Serializable
data class BlockInfo(
    val name: String,
    val hardness: Float,
    val solid: Boolean,
    val transparent: Boolean,
    val minimapColor: List<Int>,
    val modelElement: String,
    val liquid: Boolean = false,
    val viscosity: Int = 0,
)

@Serializable
data class ItemInfo(
    val buildable: Boolean,
    val placesBlock: String? = null,
)

@Serializable
data class NpcCodexInfo(
    val bbmodelFile: String,
    val behaviorKey: String,
    val width: Float,
    val height: Float,
    val wanderSpeed: Float,
    val autoSpawn: Boolean,
)

@Serializable data class BlockChange(val pos: BlockPos, val type: BlockType)

@Serializable
data class CommandInfo(
    val id: String,
    val command: String,
    val description: String,
    val autocompleteArgs: List<Int> = emptyList()
)
