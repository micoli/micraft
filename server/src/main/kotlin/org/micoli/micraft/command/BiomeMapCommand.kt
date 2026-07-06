package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class BiomeMapCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a3b4c5d6-e7f8-4901-b234-c5d6e7f8a901")
    override val name = "biomemap"
    override val description = "Toggles the biome map overlay."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.ToggleBiomeMap)
    }
}
