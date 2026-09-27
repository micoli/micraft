package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.equipment.UnwieldResult
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Hand
import org.micoli.micraft.protocol.ServerMessage.Notification

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

        val message =
            when (val result = context.loadout.unwield(session, hand)) {
                is UnwieldResult.Unwielded -> i18n.t(lang, "unwield:server:unwielded", result.item)
                UnwieldResult.Empty -> i18n.t(lang, "unwield:server:empty")
            }
        session.send(Notification(message))
    }
}
