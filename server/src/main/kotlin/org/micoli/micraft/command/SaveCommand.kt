package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SaveCommand::class.java)

class SaveCommand : CommandHandler {
    override val command = "/save"
    override val description = "Sauvegarde le monde et l'état du joueur sur disque."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        context.world.flushDirty()
        context.savePlayer(session)
        session.send(ServerMessage.Notification("World saved."))
        log.info("Manual /save by {}", session.state.name)
    }
}
