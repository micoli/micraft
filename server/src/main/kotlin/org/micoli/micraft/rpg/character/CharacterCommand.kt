package org.micoli.micraft.rpg.character

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class CharacterCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d4e5f6a7-b8c9-0123-defa-234567890bcd")
    override val command = "/character"
    override val description = "Show your RPG character sheet"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val char = session.characterData
        if (char == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "rpg:server:no_character")))
            return
        }
        val derived = DerivedStatsCalculator.compute(char)
        session.send(ServerMessage.CharacterSync(char, derived))
    }
}
