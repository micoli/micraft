package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class CodexCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d7e8f9a0-b1c2-4d3e-9f0a-1b2c3d4e5f60")
    override val name = "codex"
    override val description = "Opens the codex (blocks, items, bestiary)."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.OpenCodex)
    }
}
