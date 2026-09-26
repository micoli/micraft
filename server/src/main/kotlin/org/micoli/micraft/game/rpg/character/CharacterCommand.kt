package org.micoli.micraft.game.rpg.character

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class CharacterCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d4e5f6a7-b8c9-0123-defa-234567890bcd")
    override val name = "character"
    override val description = "Show your RPG character sheet"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        if (session.characterData == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "rpg:server:no_character")))
            return
        }
        context.characterStats.resync(session)
        session.send(ServerMessage.OpenCharacter)
    }
}
