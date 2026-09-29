package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

/** Casts the caller's Class Protection Spell (Iron Skin, Shadowstep, ...). No argument. */
class ProtectCommand : CommandHandler {
    override val id: UUID = UUID.fromString("4ae8e3ad-774f-492d-a181-156324e6f82b")
    override val name = "protect"
    override val description = "Cast your Class's Protection Spell."

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val castProtection = context.castProtection
        if (castProtection == null) {
            session.send(ServerMessage.Notification("Protection system unavailable."))
            return
        }
        castProtection(session)
    }
}
