package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ReloadCommand::class.java)

class ReloadCommand : CommandHandler {
    override val command = "/reload"
    override val description = "Recharge les fichiers de configuration sans redémarrer le serveur."
    override val options = listOf("drops.yaml — table de drops par bloc", "biomes.json — définitions des biomes")

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val reload = context.reloadConfig
        if (reload == null) {
            session.send(ServerMessage.Notification("Reload not available in this mode."))
            return
        }
        val result = reload()
        session.send(ServerMessage.Notification("Config reloaded — $result"))
        log.info("Config reloaded by {}: {}", session.state.name, result)
    }
}
