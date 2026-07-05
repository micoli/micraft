package org.micoli.micraft.rpg.character

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class SkipRpgCommand : CommandHandler {
    override val id: UUID = UUID.fromString("e5f6a7b8-c9d0-1234-efab-345678901cde")
    override val command = "/skiprpg"
    override val description = "Opt out of RPG system"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        session.state = session.state.copy(rpgOptOut = true)
        context.savePlayer(session)
        session.send(
            ServerMessage.Notification(
                context.i18n.t(session.state.language, "rpg:server:skipped_rpg")))
    }
}
