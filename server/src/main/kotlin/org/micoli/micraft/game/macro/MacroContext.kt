package org.micoli.micraft.game.macro

data class MacroContext(
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val biome: String = "",
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val currentHp: Int = 0,
    val currentMana: Int = 0,
    val effects: List<String> = emptyList(),
    /** Triggering player (bound as `player` in action-block scripts). */
    val playerName: String = "",
    val playerId: String = "",
    /** The action block running the script (bound as `self`). */
    val blockName: String = "",
    val blockX: Int = 0,
    val blockY: Int = 0,
    val blockZ: Int = 0,
    val blockVariables: Map<String, String> = emptyMap(),
)
