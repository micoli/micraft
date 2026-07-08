package org.micoli.micraft.command.commands

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.TICKS_PER_DAY
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class TimeCommand : CommandHandler {
    override val id: UUID = UUID.fromString("48666b02-7c6d-4a88-b162-26c3ff56bd9a")
    override val name = "time"
    override val permission = "admin"
    override val description = "Shows or sets the in-game time."
    override val usage = "$command [0-23]"
    override val options = (0..23).map { it.toString() }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val trimmed = args.trim()

        if (trimmed.isEmpty()) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(
                        lang, "time:server:current", ticksToDisplay(context.getGameTime()))))
            return
        }

        val hour = trimmed.toIntOrNull()
        if (hour == null || hour !in 0..23) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "time:server:usage")))
            return
        }

        val newTicks = hour.toLong() * TICKS_PER_DAY / 24L
        context.setGameTime(newTicks)
        context.broadcast(ServerMessage.TimeUpdate(newTicks))
        session.send(
            ServerMessage.Notification(
                context.i18n.t(lang, "time:server:set", ticksToDisplay(newTicks))))
    }

    private fun ticksToDisplay(ticks: Long): String {
        val day = ticks % TICKS_PER_DAY
        val h = (day * 24 / TICKS_PER_DAY).toInt()
        val m = ((day * 24 * 60 / TICKS_PER_DAY) % 60).toInt()
        return "%02d:%02d".format(h, m)
    }
}
