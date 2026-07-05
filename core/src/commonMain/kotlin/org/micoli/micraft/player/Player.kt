package org.micoli.micraft.player

import kotlinx.serialization.Serializable
import org.micoli.micraft.player.rpg.CharacterData
import org.micoli.micraft.ui.GameLayout
import org.micoli.micraft.ui.defaultLayout
import org.micoli.micraft.world.ItemType

@Serializable data class Vec3(val x: Float, val y: Float, val z: Float)

@Serializable data class Orientation(val yaw: Float, val pitch: Float)

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
    val shortcutBar: List<ItemType?> = List(10) { null },
    val layouts: List<GameLayout> = listOf(defaultLayout()),
    val activeLayout: String = "default",
    val subscribedChannels: List<String> = listOf("world", "system", "game"),
    val disabledCommands: Set<String> = emptySet(),
    val viewMode: String = "FIRST_PERSON",
    val skin: String = "player",
    val armors: List<String> = emptyList(),
    val animatedFavicon: Boolean = true,
    val chunkDebugVisible: Boolean = false,
    val knownRecipes: Set<String> = emptySet(),
    val characterData: CharacterData? = null,
    val rpgOptOut: Boolean = true,
)
