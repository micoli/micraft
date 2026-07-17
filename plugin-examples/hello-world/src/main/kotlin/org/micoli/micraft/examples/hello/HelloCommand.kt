package org.micoli.micraft.examples.hello

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class HelloCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001")
    override val name = "hello"
    override val description = "Greet the player"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.send(ServerMessage.Notification("Hello ${session.state.name}!"))
    }
}
