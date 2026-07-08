package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession

class DisconnectCommand : CommandHandler {
    override val id: UUID = UUID.fromString("11791e31-40bb-4b56-9def-2c57d0c06957")
    override val name = "disconnect"
    override val description = "Déconnecte le joueur courant."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        context.kickSession(session.state.name)
    }
}
