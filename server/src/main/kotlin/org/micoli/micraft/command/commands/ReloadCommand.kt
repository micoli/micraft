package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ReloadCommand::class.java)

class ReloadCommand : CommandHandler {
    override val id: UUID = UUID.fromString("ca8872ff-542c-4581-bf05-0cc68ea01f60")
    override val name = "reload"
    override val permission = "admin"
    override val description = "Reloads configuration files without restarting the server."
    override val options =
        listOf(
            "resources/blocks/*.yaml — block properties + drop tables",
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
        val result = reload(lang)
        session.send(ServerMessage.Notification(context.i18n.t(lang, "reload:server:done", result)))
        log.info("Config reloaded by {}: {}", session.state.name, result)
    }
}
