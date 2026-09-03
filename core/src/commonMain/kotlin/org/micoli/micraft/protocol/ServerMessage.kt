package org.micoli.micraft.protocol

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
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
import org.micoli.micraft.placeable.PlaceableState
import org.micoli.micraft.placeable.siege.SiegeProjectileState
import org.micoli.micraft.placeable.siege.SiegeWeaponState
import org.micoli.micraft.player.ChannelSubscription
import org.micoli.micraft.player.EditMode
import org.micoli.micraft.player.Hand
import org.micoli.micraft.player.PlayerStance
import org.micoli.micraft.player.PlayerState
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.player.rpg.BaseStats
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.player.rpg.DerivedStats
import org.micoli.micraft.quest.QuestProgress
import org.micoli.micraft.social.FactionDefinition
import org.micoli.micraft.social.FactionState
import org.micoli.micraft.social.GroupInfo
import org.micoli.micraft.social.GuildInfoDto
import org.micoli.micraft.ui.GameLayout
import org.micoli.micraft.ui.defaultLayout
import org.micoli.micraft.vehicle.VehicleState

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
        val buildTimestamp: String = "",
        val maxInteractionDistance: Double = 7.0,
    ) : ServerMessage()

    @ProtoId(1) @Serializable data class ShadersUpdate(val enabled: Boolean) : ServerMessage()

    @ProtoId(2)
    @Serializable
    data class ChunkData(
        val pos: ChunkPos,
        val topY: Int,
        val wireBlocks: ByteArray,
        val wireStates: ByteArray = ByteArray(0),
        val entities: List<BlockEntityProto> = emptyList(),
        val wireExtraStates: ByteArray = ByteArray(0),
    ) : ServerMessage() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ChunkData

            if (topY != other.topY) return false
            if (pos != other.pos) return false
            if (!wireBlocks.contentEquals(other.wireBlocks)) return false
            if (!wireStates.contentEquals(other.wireStates)) return false
            if (!wireExtraStates.contentEquals(other.wireExtraStates)) return false
            if (entities != other.entities) return false

            return true
        }

        override fun hashCode(): Int {
            var result = topY
            result = 31 * result + pos.hashCode()
            result = 31 * result + wireBlocks.contentHashCode()
            result = 31 * result + wireStates.contentHashCode()
            result = 31 * result + wireExtraStates.contentHashCode()
            result = 31 * result + entities.hashCode()
            return result
        }
    }

    @ProtoId(3)
    @Serializable
    data class PlayerUpdate(val state: PlayerState, val lastProcessedSeq: Long = 0) :
        ServerMessage()

    @ProtoId(4)
    @Serializable
    data class WorldUpdate(
        val changes: List<BlockChange>,
        val entityAdds: List<BlockEntityProto> = emptyList(),
        val entityRemoves: List<BlockPos> = emptyList(),
        val entityRemovesAt: List<EntityRemoveAt> = emptyList(),
    ) : ServerMessage()

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
    data class ShortcutBarUpdate(val pages: Map<Int, Map<Int, ShortcutSlot>> = emptyMap()) :
        ServerMessage()

    @ProtoId(15)
    @Serializable
    data class LayoutsSync(val layouts: List<GameLayout>, val activeLayout: String) :
        ServerMessage()

    @ProtoId(16) @Serializable object OpenLayoutEditor : ServerMessage()

    @ProtoId(17) @Serializable object OpenPreferences : ServerMessage()

    @ProtoId(18) @Serializable object OpenCodex : ServerMessage()

    @ProtoId(28) @Serializable object OpenCraft : ServerMessage()

    @ProtoId(27) @Serializable object ToggleIngameMap : ServerMessage()

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
        val npcWalkBones: Map<String, Map<String, String>> = emptyMap(),
        val vehicles: Map<String, String> = emptyMap(),
        val vehicleDefinitions: Map<String, VehicleCodexInfo> = emptyMap(),
        val plainColors: List<PlainColorInfo> = emptyList(),
        /** See WorldConstants.IMPOSTOR_SKIRT_DEPTH. */
        val impostorSkirtDepth: Int = 12,
        val placeables: Map<String, String> = emptyMap(),
        val placeableDefinitions: Map<String, PlaceableCodexInfo> = emptyMap(),
        val siegeProjectiles: Map<String, String> = emptyMap(),
        val siegeProjectileDefinitions: Map<String, SiegeProjectileCodexInfo> = emptyMap(),
        val siegeWeaponDefinitions: Map<String, SiegeWeaponCodexInfo> = emptyMap(),
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
        val statisticsVisible: Boolean = false,
        val attackPanelVisible: Boolean = false,
        val macros: Map<String, String> = emptyMap(),
        val fieldOfView: Int = 70,
        val defaultKeybindings: Map<String, List<String>> = emptyMap(),
        val dynamicFogEnabled: Boolean = true,
        val autoTargetEnabled: Boolean = true,
        val inventorySortA: String = "",
        val inventorySortB: String = "",
        val shadowAngleDeg: Int = 1,
        val overrideViewRadius: Int? = null,
        val overrideForwardViewRadius: Int? = null,
        val overrideUseImpostor: Boolean? = null,
        val overrideImpostorRadiusChunks: Int? = null,
        val overrideImpostorFovBonusChunks: Int? = null,
        val continuousBreak: Boolean = false,
        val dominantHand: Hand = Hand.RIGHT,
        val disabledViewModes: Set<String> = emptySet(),
        val turnSpeedHorizontal: Float = 2.5f,
        val turnSpeedVertical: Float = 1.2f,
    ) : ServerMessage()

    @ProtoId(25)
    @Serializable
    data class WeatherUpdate(val zones: List<WeatherZoneInfo>) : ServerMessage()

    @ProtoId(26)
    @Serializable
    data class GameConfigSync(
        val reconcileToleranceXz: Double,
        val reconcileToleranceY: Double,
        val maxInteractionDistance: Double = 7.0,
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
        val level: Int? = null,
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
        val godMode: Boolean = false,
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

    @ProtoId(42) @Serializable data class LightBoostUpdate(val enabled: Boolean) : ServerMessage()

    @ProtoId(43)
    @Serializable
    data class QuestSync(val quests: Map<String, QuestProgress>) : ServerMessage()

    @ProtoId(44)
    @Serializable
    data class QuestUpdate(val questId: String, val progress: QuestProgress) : ServerMessage()

    @ProtoId(45) @Serializable object OpenQuestJournal : ServerMessage()

    @ProtoId(46)
    @Serializable
    data class AoEEffect(
        val x: Float,
        val y: Float,
        val z: Float,
        val radius: Float,
    ) : ServerMessage()

    @ProtoId(47) @Serializable data class GodModeUpdate(val enabled: Boolean) : ServerMessage()

    @ProtoId(56) @Serializable data class EditModeUpdate(val mode: EditMode) : ServerMessage()

    @ProtoId(48) @Serializable data class WalletUpdate(val copper: Long) : ServerMessage()

    @ProtoId(49) @Serializable data class MailSync(val mails: List<MailMessage>) : ServerMessage()

    @ProtoId(50) @Serializable data class MailReceived(val mail: MailMessage) : ServerMessage()

    @ProtoId(51) @Serializable data class MailUpdate(val mail: MailMessage) : ServerMessage()

    @ProtoId(52) @Serializable data class MailDeleted(val mailId: String) : ServerMessage()

    @ProtoId(53) @Serializable object OpenMailbox : ServerMessage()

    @ProtoId(54)
    @Serializable
    data class AdminZoneWireframe(val zone: InstanceZoneProto?) : ServerMessage()

    // Sent to admins on connect and whenever an instance zone is created/renamed/deleted/resized,
    // so the minimap can outline every zone regardless of whether the player is standing inside
    // one — unlike AdminZoneWireframe, which only ever carries the single zone the player is in.
    @ProtoId(55)
    @Serializable
    data class InstanceZonesSync(val zones: List<InstanceZoneProto>) : ServerMessage()

    @ProtoId(57)
    @Serializable
    data class VehicleSpawned(val vehicle: VehicleState) : ServerMessage()

    @ProtoId(58)
    @Serializable
    data class VehicleUpdate(val vehicle: VehicleState) : ServerMessage()

    @ProtoId(59) @Serializable data class VehicleDespawned(val id: String) : ServerMessage()

    /** Sent to the mounting/dismounting player only — `null` on dismount. */
    @ProtoId(62) @Serializable data class MountUpdate(val vehicleId: String?) : ServerMessage()

    // Sent to admins on connect so the creative-mode "Scene" tab can list placeable scenes.
    @ProtoId(60)
    @Serializable
    data class ScenesSync(val scenes: List<SceneSummaryProto>) : ServerMessage()

    // Non-air blocks/states of a scene, sent on demand when a player selects it in the
    // creative-mode "Scene" tab, to build the local ghost preview mesh.
    @ProtoId(61)
    @Serializable
    data class ScenePreviewData(
        val sceneId: String,
        val width: Int,
        val height: Int,
        val depth: Int,
        val blocks: ByteArray,
        val states: ByteArray,
    ) : ServerMessage()

    @ProtoId(63)
    @Serializable
    data class PlaceableSpawned(val state: PlaceableState) : ServerMessage()

    @ProtoId(64)
    @Serializable
    data class PlaceableUpdate(val state: PlaceableState) : ServerMessage()

    @ProtoId(65) @Serializable data class PlaceableDespawned(val id: String) : ServerMessage()

    @ProtoId(66)
    @Serializable
    data class SiegeWeaponUpdate(val state: SiegeWeaponState) : ServerMessage()

    /**
     * Phase B placeholder for a successful fire — carries the computed muzzle position/launch
     * velocity so the event is visible/testable end-to-end before Phase C's real projectile system
     * exists. Superseded by [SiegeProjectileSpawned] (Phase C), kept only as a lightweight "shot
     * fired" notice — [org.micoli.micraft.game.placeable.siege.SiegeWeaponManager.fire] still
     * broadcasts it alongside the real projectile spawn.
     */
    @ProtoId(67)
    @Serializable
    data class SiegeWeaponFired(val weaponId: String, val muzzle: Vec3, val velocity: Vec3) :
        ServerMessage()

    @ProtoId(68)
    @Serializable
    data class SiegeProjectileSpawned(val projectile: SiegeProjectileState) : ServerMessage()

    @ProtoId(69)
    @Serializable
    data class SiegeProjectileUpdate(val projectile: SiegeProjectileState) : ServerMessage()

    @ProtoId(70)
    @Serializable
    data class SiegeProjectileImpact(val x: Float, val y: Float, val z: Float, val radius: Float) :
        ServerMessage()

    @ProtoId(71) @Serializable object OpenAuctionHouse : ServerMessage()

    @ProtoId(72)
    @Serializable
    data class AuctionListingsUpdate(val listings: List<AuctionListing>) : ServerMessage()

    /**
     * Sent to a player on connect and whenever a claim they own/trust is created/abandoned/updated.
     */
    @ProtoId(73) @Serializable data class ClaimSync(val claims: List<ClaimInfo>) : ServerMessage()

    @ProtoId(74) @Serializable data class ClaimDenied(val reason: String) : ServerMessage()

    @ProtoId(75) @Serializable object OpenCharacter : ServerMessage()

    /** Sent on connect and on every membership/leadership change. `null` = player has no group. */
    @ProtoId(76) @Serializable data class GroupSync(val group: GroupInfo? = null) : ServerMessage()

    @ProtoId(77)
    @Serializable
    data class GroupInviteReceived(val groupId: String, val fromName: String) : ServerMessage()

    /** Sent on connect and on every guild change to members. `null` = player has no guild. */
    @ProtoId(78)
    @Serializable
    data class GuildSync(val guild: GuildInfoDto? = null) : ServerMessage()

    @ProtoId(79)
    @Serializable
    data class GuildInviteReceived(
        val guildId: String,
        val guildName: String,
        val fromName: String,
    ) : ServerMessage()

    @ProtoId(80)
    @Serializable
    data class FactionSync(
        val enabled: Boolean,
        val definitions: List<FactionDefinition>,
        val states: List<FactionState>,
        val myFactionId: String? = null,
        val changeCooldownRemainingMs: Long = 0L,
    ) : ServerMessage()

    @ProtoId(81)
    @Serializable
    data class SocialDenied(val scope: String, val reason: String) : ServerMessage()

    /**
     * Sent on connect and on every roster change: the player's tamed pets and which one is active.
     */
    @ProtoId(82)
    @Serializable
    data class PetRosterSync(
        val pets: List<PetInfo>,
        val activePetId: String? = null,
    ) : ServerMessage()

    /** Full list of named action blocks — sent on connect and after any registry change. */
    @ProtoId(83)
    @Serializable
    data class ActionBlockSync(
        val blocks: List<org.micoli.micraft.game.world.actionblock.ActionBlockInfo>,
    ) : ServerMessage()

    /** Incremental add/rename of one action block. */
    @ProtoId(84)
    @Serializable
    data class ActionBlockUpsert(
        val info: org.micoli.micraft.game.world.actionblock.ActionBlockInfo,
    ) : ServerMessage()

    /** One action block was removed (block broken or unnamed). */
    @ProtoId(85) @Serializable data class ActionBlockRemove(val pos: BlockPos) : ServerMessage()

    /** Fills the action-block editor form; [error] set when a save was rejected. */
    @ProtoId(86)
    @Serializable
    data class ActionBlockPayload(
        val pos: BlockPos,
        val name: String,
        val onActivate: String = "",
        val onTargetEvent: String = "",
        val onRemoteEvent: String = "",
        val variables: Map<String, String> = emptyMap(),
        val error: String? = null,
    ) : ServerMessage()
}

@Serializable
data class PetInfo(
    val id: String,
    val name: String,
    val npcType: String,
    val level: Int,
    val xp: Int,
    val currentHp: Int,
    val maxHp: Int,
    val spawned: Boolean,
    val dead: Boolean,
    val resurrectReadyAtMs: Long,
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class ClaimInfo(
    val id: String,
    val chunks: List<ChunkPos>,
    val yMin: Int,
    val yMax: Int,
    val ownerId: String,
    val ownerName: String,
    // Default Json (encodeDefaults=false) drops fields equal to their default — without this, an
    // empty trusted list (the common case) would be omitted from the JSON GameClient hands to
    // window.mc.claimSync, and the TS side would see `undefined` instead of `[]`.
    @EncodeDefault(ALWAYS) val trustedPlayerNames: List<String> = emptyList(),
)

@Serializable
data class SceneSummaryProto(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val depth: Int
)

@Serializable
data class InstanceZoneProto(
    val id: String,
    val name: String,
    val yMin: Int,
    val yMax: Int,
    val chunks: List<ChunkPos>,
)

@Serializable
data class BlockInfo(
    val name: String,
    val hardness: Float,
    val solid: Boolean,
    val transparent: Boolean,
    val minimapColor: List<Int>,
    val modelElement: String,
    val gltfModel: String = "",
    val liquid: Boolean = false,
    val viscosity: Int = 0,
    val minimapVisible: Boolean = true,
    val rotatable: Boolean = false,
    val hasStuds: Boolean = false,
    val brickSize: List<Float> = listOf(2f, 2f, 2f),
    val plainColorable: Boolean = false,
    val isCubic: Boolean = true,
    val topColor: List<Int> = minimapColor,
    val sideColor: List<Int> = minimapColor,
    val rail: RailInfo? = null,
)

/** Wire form of [org.micoli.micraft.game.world.rail.RailDefinition]. */
@Serializable
data class RailInfo(val connections: List<List<String>> = emptyList(), val height: Float = 1f)

/** Palette entry; its position in [ServerMessage.RegistrySync.plainColors] is colorIndex - 1. */
@Serializable data class PlainColorInfo(val name: String, val hex: String)

@Serializable
data class BlockEntityProto(
    val worldX: Int,
    val worldY: Int,
    val worldZ: Int,
    val type: String,
    val sizeX: Int,
    val sizeY: Int = 1,
    val sizeZ: Int = 1,
    val rotation: Int = 0,
    val yOffset: Int = 0,
    val xOffset: Int = 0,
    val zOffset: Int = 0,
    val colorIndex: Int = 0,
)

@Serializable
data class EntityRemoveAt(
    val pos: BlockPos,
    val yOffset: Int = 0,
    val xOffset: Int = 0,
    val zOffset: Int = 0
)

@Serializable
data class ItemInfo(
    val buildable: Boolean,
    val placesBlock: String? = null,
    val plainColor: String? = null,
    val consumable: Boolean = false,
    val spawnsEntity: String? = null,
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

@Serializable
data class VehicleCodexInfo(
    val bbmodelFile: String,
    val width: Float,
    val height: Float,
    val speed: Float,
)

@Serializable
data class PlaceableCodexInfo(
    val bbmodelFile: String,
    val width: Float,
    val height: Float,
)

@Serializable
data class SiegeProjectileCodexInfo(
    val bbmodelFile: String,
    val radius: Float,
)

/**
 * Launch-math stats a siege weapon type needs client-side to render Phase D's trajectory preview —
 * mirrors the subset of [org.micoli.micraft.placeable.siege.SiegeWeaponDefinition] consumed by
 * `SiegeWeaponManager.computeMuzzleAndVelocity` (server). Keyed by [EntityType] id in
 * [RegistrySync.siegeWeaponDefinitions], same key as [RegistrySync.placeableDefinitions] since a
 * siege weapon always composes with a placeable of the same type.
 */
@Serializable
data class SiegeWeaponCodexInfo(
    val muzzleOffset: Vec3,
    val launchPower: Float,
    val launchPitchDeg: Float,
)

@Serializable
data class BlockChange(
    val pos: BlockPos,
    val type: BlockType,
    val state: Byte = 0,
    val extraState: Byte = 0,
)

@Serializable
data class CommandInfo(
    val id: String,
    val command: String,
    val description: String,
    val autocompleteArgs: List<Int> = emptyList()
)
