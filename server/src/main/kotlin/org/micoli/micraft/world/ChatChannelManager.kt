package org.micoli.micraft.world

class ChatChannelManager {
    private val customChannels = mutableSetOf<String>()

    companion object {
        val BUILTIN = setOf("world", "around", "system", "game")
        val PROTECTED = setOf("system", "game")
    }

    fun channelExists(name: String): Boolean = name in BUILTIN || name in customChannels || name.startsWith("dm:")

    fun registerChannel(name: String) { customChannels.add(name) }

    fun listKnownChannels(): List<String> = (BUILTIN + customChannels).sorted()

    fun dmChannelName(a: String, b: String): String = "dm:${listOf(a, b).sorted().joinToString(":")}"
}
