package org.micoli.micraft.player

import kotlinx.serialization.Serializable
import org.micoli.micraft.combat.ShortcutSlot
import org.micoli.micraft.game.world.ItemType
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.quest.QuestProgress
import org.micoli.micraft.ui.GameLayout
import org.micoli.micraft.ui.defaultLayout

@Serializable data class Vec3(val x: Float, val y: Float, val z: Float)

@Serializable data class Orientation(val yaw: Float, val pitch: Float)

@Serializable data class ChannelSubscription(val name: String, val autoFocus: Boolean = false)

fun List<ChannelSubscription>.hasChannel(name: String): Boolean = any { it.name == name }

@Serializable
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
    val skin: String = "player",
    val armors: List<String> = emptyList(),
    val animatedFavicon: Boolean = false,
    val chunkDebugVisible: Boolean = false,
    val statisticsVisible: Boolean = false,
    val attackPanelVisible: Boolean = true,
    val dynamicFogEnabled: Boolean = true,
    val fieldOfView: Int = 70,
    val knownRecipes: Set<String> = emptySet(),
    val characterData: CharacterData? = null,
    val rpgOptOut: Boolean = true,
    val godMode: Boolean = false,
    val lightBoostEnabled: Boolean = false,
    val email: String = "",
    val zoneLevel: Int = 0,
    val quests: Map<String, QuestProgress> = emptyMap(),
    val autoTargetEnabled: Boolean = true,
)
