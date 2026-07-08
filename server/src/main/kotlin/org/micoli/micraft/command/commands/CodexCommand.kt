package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class CodexCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d7e8f9a0-b1c2-4d3e-9f0a-1b2c3d4e5f60")
    override val name = "codex"
    override val description = "Opens the codex (blocks, items, bestiary)."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenCodex)
    }
}
