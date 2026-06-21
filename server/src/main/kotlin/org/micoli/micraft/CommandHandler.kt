package org.micoli.micraft

import org.micoli.micraft.session.PlayerSession

interface CommandHandler {
    val command: String
    val description: String get() = ""
    val usage: String get() = command
    val options: List<String> get() = emptyList()
    suspend fun execute(session: PlayerSession, args: String, context: CommandContext)
}
