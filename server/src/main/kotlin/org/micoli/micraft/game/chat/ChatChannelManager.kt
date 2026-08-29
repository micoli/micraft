package org.micoli.micraft.game.chat

class ChatChannelManager {
    private val customChannels = mutableSetOf<String>()

    companion object {
        val BUILTIN = setOf("world", "around", "system", "game", "combat")
        val PROTECTED = setOf("system", "game")
        val DYNAMIC_PREFIXES = listOf("dm:", "group:", "guild:", "faction:")
    }

    fun channelExists(name: String): Boolean =
        name in BUILTIN || name in customChannels || DYNAMIC_PREFIXES.any { name.startsWith(it) }

    fun registerChannel(name: String) {
        customChannels.add(name)
    }

    fun unregisterChannel(name: String) {
        customChannels.remove(name)
    }

    fun listKnownChannels(): List<String> = (BUILTIN + customChannels).sorted()

    fun dmChannelName(a: String, b: String): String =
        "dm:${listOf(a, b).sorted().joinToString(":")}"
}
