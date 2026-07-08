package org.micoli.micraft.game.rpg.character

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class SkipRpgCommand : CommandHandler {
    override val id: UUID = UUID.fromString("e5f6a7b8-c9d0-1234-efab-345678901cde")
    override val name = "skiprpg"
    override val description = "Opt out of RPG system"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.state = session.state.copy(rpgOptOut = true)
        context.savePlayer(session)
        session.send(
            ServerMessage.Notification(
                context.i18n.t(session.state.language, "rpg:server:skipped_rpg")))
    }
}
