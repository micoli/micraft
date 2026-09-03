package org.micoli.micraft.game.macro

import kotlinx.serialization.Serializable

@Serializable
data class MacroContextVar(
    val name: String,
    val type: String,
    val children: List<String> = emptyList(),
)

val MACRO_CONTEXT_SCHEMA: List<MacroContextVar> =
    listOf(
        MacroContextVar("position", "{ x, y, z }", listOf("x", "y", "z")),
        MacroContextVar("biome", "String"),
        MacroContextVar("yaw", "Float"),
        MacroContextVar("pitch", "Float"),
        MacroContextVar("currentHp", "Int"),
        MacroContextVar("currentMana", "Int"),
        MacroContextVar("effects", "List<String>"),
        MacroContextVar("player", "{ name, id, hp, mana }", listOf("name", "id", "hp", "mana")),
        MacroContextVar("self", "{ name, x, y, z, vars }", listOf("name", "x", "y", "z", "vars")),
    )
