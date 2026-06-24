package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ReloadCommand::class.java)

class ReloadCommand : CommandHandler {
    override val id = UUID.fromString("ca8872ff-542c-4581-bf05-0cc68ea01f60")
    override val command = "/reload"
    override val description = "Reloads configuration files without restarting the server."
    override val options =
        listOf(
            "drops.yaml — block drop table",
            "biomes.yaml — biome definitions",
            "i18n/*.yaml — translations")

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val reload = context.reloadConfig
        if (reload == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "reload:server:unavailable")))
            return
        }
        val result = reload()
        session.send(ServerMessage.Notification(context.i18n.t(lang, "reload:server:done", result)))
        log.info("Config reloaded by {}: {}", session.state.name, result)
    }
}
