package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class GodOnCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b7e3f1a2-4c5d-6e7f-8a9b-0c1d2e3f4a5b")
    override val name = "god:on"
    override val permission = "admin"
    override val description = "Enable god mode (immune to damage)."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        session.state = session.state.copy(godMode = true)
        context.savePlayer(session)
        session.send(ServerMessage.GodModeUpdate(true))
        session.send(ServerMessage.Notification(context.i18n.t(lang, "god:server:on")))
    }
}

class GodOffCommand : CommandHandler {
    override val id: UUID = UUID.fromString("c8f4a2b3-5d6e-7f8a-9b0c-1d2e3f4a5b6c")
    override val name = "god:off"
    override val permission = "admin"
    override val description = "Disable god mode."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        session.state = session.state.copy(godMode = false)
        context.savePlayer(session)
        session.send(ServerMessage.GodModeUpdate(false))
        session.send(ServerMessage.Notification(context.i18n.t(lang, "god:server:off")))
    }
}
