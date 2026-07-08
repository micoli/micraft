package org.micoli.micraft.macro

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
)
