package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.session.PlayerSession
import java.util.UUID

class DisconnectCommand : CommandHandler {
    override val id = UUID.fromString("11791e31-40bb-4b56-9def-2c57d0c06957")
    override val command = "/disconnect"
    override val description = "Déconnecte le joueur courant."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        context.kickSession(session.state.name)
    }
}
