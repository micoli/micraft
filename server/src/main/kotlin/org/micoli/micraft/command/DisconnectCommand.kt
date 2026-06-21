package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.session.PlayerSession

class DisconnectCommand : CommandHandler {
    override val command = "/disconnect"
    override val description = "Déconnecte le joueur courant."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        context.kickSession(session.state.name)
    }
}
