package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ShadersCommand::class.java)

class ShadersCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b2a1d2bb-1912-4ca2-8b60-8b2012b2ab30")
    override val name = "shaders"
    override val description =
        "Toggles visual shaders (ambient occlusion, directional shading, fog)."
    override val usage = "$command [on|off]"
    override val options = listOf("on", "off")

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val i18n = context.i18n
        val lang = session.state.language
        val enabled =
            when (args.trim().lowercase()) {
                "on" -> true
                "off" -> false
                "" -> !session.state.shadersEnabled
                else -> {
                    session.send(ServerMessage.Notification(i18n.t(lang, "shaders:server:usage")))
                    return
                }
            }
        session.state = session.state.copy(shadersEnabled = enabled)
        context.savePlayer(session)
        session.send(ServerMessage.ShadersUpdate(enabled))
        val key = if (enabled) "shaders:server:enabled" else "shaders:server:disabled"
        session.send(ServerMessage.Notification(i18n.t(lang, key)))
        log.info("{} shaders={}", session.state.name, enabled)
    }
}
