package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Hand
import org.micoli.micraft.protocol.ServerMessage.Notification
import org.micoli.micraft.protocol.ServerMessage.PlayerUpdate

class UnwieldCommand : CommandHandler {
    override val id: UUID = UUID.fromString("e6b1c3d4-8f9a-4b0c-9d2e-4f5a6b7c8d9e")
    override val name = "unwield"
    override val description = "Empty a hand slot."
    override val usage = "$command <hand>"
    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<String> =
        if (argIndex == 0)
            listOf("left", "right").filter { it.contains(partial, ignoreCase = true) }
        else emptyList()

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val hand =
            when (args.trim().lowercase()) {
                "left" -> Hand.LEFT
                "right" -> Hand.RIGHT
                else -> null
            }

        if (hand == null) {
            session.send(Notification(i18n.t(lang, "unwield:server:usage")))
            return
        }

        val current =
            if (hand == Hand.RIGHT) session.state.rightHandItem else session.state.leftHandItem
        if (current == null) {
            session.send(Notification(i18n.t(lang, "unwield:server:empty")))
            return
        }

        session.state =
            when (hand) {
                Hand.RIGHT -> session.state.copy(rightHandItem = null)
                Hand.LEFT -> session.state.copy(leftHandItem = null)
            }
        context.broadcast(PlayerUpdate(session.state))
        context.savePlayer(session)
        session.send(Notification(i18n.t(lang, "unwield:server:unwielded", current)))
    }
}
