package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.EditMode
import org.micoli.micraft.protocol.ServerMessage

class ModeCommand : CommandHandler {
    override val id: UUID = UUID.fromString("d4a1c6b2-8e3f-4a7b-9c2d-1e5f6a7b8c9d")
    override val name = "mode"
    override val permission = "admin"
    override val description = "Switch between normal game mode and creative edit mode. (admin)"
    override val usage = "$command <game|creative>"
    override val options = listOf("game", "creative")

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val trimmed = args.trim().lowercase()
        val mode = runCatching { EditMode.valueOf(trimmed.uppercase()) }.getOrNull()
        if (mode == null) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "mode:server:usage")))
            return
        }
        session.state = session.state.copy(editMode = mode)
        if (mode == EditMode.GAME) session.creativeFocusPos = null
        context.savePlayer(session)
        session.send(ServerMessage.EditModeUpdate(mode))
        val key = if (mode == EditMode.CREATIVE) "mode:server:creative" else "mode:server:game"
        session.send(ServerMessage.Notification(context.i18n.t(lang, key)))
    }
}
