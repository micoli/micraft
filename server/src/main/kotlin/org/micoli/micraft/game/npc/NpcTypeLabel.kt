package org.micoli.micraft.game.npc

/** "wolf_man" -> "Wolf man" — the one human-readable rendering of an NPC type key. */
fun humanizeNpcType(type: String): String =
    type.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
