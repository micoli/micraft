package org.micoli.micraft

import org.micoli.micraft.session.PlayerSession
import java.util.UUID

interface CommandHandler {
    val id: UUID
    val command: String
    val description: String get() = ""
    val usage: String get() = command
    val options: List<String> get() = emptyList()
    suspend fun execute(session: PlayerSession, args: String, context: CommandContext)
}
