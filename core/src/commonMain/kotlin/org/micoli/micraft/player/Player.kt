package org.micoli.micraft.player

import kotlinx.serialization.Serializable
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.quest.QuestProgress
import org.micoli.micraft.schema.JsonSchemaConstraint
import org.micoli.micraft.schema.JsonSchemaOpen
import org.micoli.micraft.ui.GameLayout
import org.micoli.micraft.ui.defaultLayout

@Serializable data class Vec3(val x: Float, val y: Float, val z: Float)

@Serializable data class Orientation(val yaw: Float, val pitch: Float)

@Serializable data class ChannelSubscription(val name: String, val autoFocus: Boolean = false)

fun List<ChannelSubscription>.hasChannel(name: String): Boolean = any { it.name == name }

@Serializable
@JsonSchemaOpen
data class PlayerState(
    val id: String,
    val name: String,
    val pos: Vec3,
    val orientation: Orientation,
    val stance: PlayerStance = PlayerStance.STANDING,
    val flying: Boolean = false,
    val speedMultiplier: Float = 1f,
    val biome: String = "",
    val inventory: Map<ItemType, Int> = emptyMap(),
    val language: String = "en",
    val shadersEnabled: Boolean = true,
    val shortcutBar: List<ShortcutSlot?> = List(10) { null },
    val shortcutBarPages: List<List<ShortcutSlot?>> = List(10) { List(10) { null } },
    val layouts: List<GameLayout> = listOf(defaultLayout()),
    val activeLayout: String = "default",
    val subscribedChannels: List<ChannelSubscription> =
        listOf(
            ChannelSubscription("world"),
            ChannelSubscription("system"),
            ChannelSubscription("game")),
    val disabledCommands: Set<String> = emptySet(),
    val viewMode: String = "FIRST_PERSON",
    val disabledViewModes: Set<String> = emptySet(),
    val skin: String = "articulated",
    val armors: List<String> = emptyList(),
    val ownedArmors: List<String> = emptyList(),
    val ownedWeapons: List<String> = emptyList(),
    val ownedTools: List<String> = emptyList(),
    val animatedFavicon: Boolean = false,
    val chunkDebugVisible: Boolean = false,
    val statisticsVisible: Boolean = false,
    val attackPanelVisible: Boolean = true,
    val dynamicFogEnabled: Boolean = true,
    @JsonSchemaConstraint(minimum = 1.0, maximum = 180.0) val fieldOfView: Int = 70,
    val knownRecipes: Set<String> = emptySet(),
    val characterData: CharacterData? = null,
    val rpgOptOut: Boolean = true,
    val godMode: Boolean = false,
    val editMode: EditMode = EditMode.GAME,
    val lightBoostEnabled: Boolean = false,
    val email: String = "",
    val zoneLevel: Int = 0,
    val quests: Map<String, QuestProgress> = emptyMap(),
    val autoTargetEnabled: Boolean = true,
    val inventorySortA: String = "",
    val inventorySortB: String = "",
    val wallet: Long = 0L,
    val shadowAngleDeg: Int = 1,
    val overrideViewRadius: Int? = null,
    val overrideForwardViewRadius: Int? = null,
    val overrideUseImpostor: Boolean? = null,
    val overrideImpostorRadiusChunks: Int? = null,
    val overrideImpostorFovBonusChunks: Int? = null,
    val continuousBreak: Boolean = false,
    val dominantHand: Hand = Hand.RIGHT,
    @JsonSchemaConstraint(minimum = 0.5, maximum = 10.0) val turnSpeedHorizontal: Float = 2.5f,
    @JsonSchemaConstraint(minimum = 0.5, maximum = 10.0) val turnSpeedVertical: Float = 1.2f,
    val rightHandItem: String? = null,
    val leftHandItem: String? = null,
    val mounted: Boolean = false,
    val guildId: String? = null,
    val guildRank: String? = null,
    val guildTag: String? = null,
    val factionId: String? = null,
    val factionChangedAtMs: Long? = null,
)
