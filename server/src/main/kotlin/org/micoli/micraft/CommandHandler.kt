package org.micoli.micraft

import org.micoli.micraft.session.PlayerSession

interface CommandHandler {
    val command: String
    suspend fun execute(session: PlayerSession, args: String, context: CommandContext)
}
