package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SaveCommand::class.java)

class SaveCommand : CommandHandler {
    override val command = "/save"
    override val description = "Saves the world and player state to disk."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        context.flushWorld?.invoke() ?: context.world.flushDirty()
        context.savePlayer(session)
        session.send(ServerMessage.Notification(context.i18n.t(session.state.language, "save:server:done")))
        log.info("Manual /save by {}", session.state.name)
    }
}
