package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SaveCommand::class.java)

class SaveCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c4111a06-2ab9-4622-a6a1-7613f995f5ac")
    override val name = "save"
    override val description = "Saves the world and player state to disk."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        context.flushWorld?.invoke() ?: context.world.flushDirty()
        context.savePlayer(session)
        session.send(
            ServerMessage.Notification(context.i18n.t(session.state.language, "save:server:done")))
        log.info("Manual /save by {}", session.state.name)
    }
}
