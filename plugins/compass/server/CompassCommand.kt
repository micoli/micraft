package org.micoli.micraft.plugins.compass

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.protocol.ServerMessage

class CompassCommand : PluginCommand {
    override val id: UUID = UUID.fromString("f1a2b3c4-d5e6-4788-9a0b-1c2d3e4f5a6b")
    override val name = "compass"
    override val command = "/compass"
    override val description = "Points the compass widget at coordinates or a named point."
    override val usage = "/compass <x y z | pointName | clear>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<Completion> {
        if (argIndex != 0) return emptyList()
        val names = context.namedPoints().keys.toList() + "clear"
        return names.filter { it.contains(partial, ignoreCase = true) }.map { Completion(it) }
    }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val target = args.trim()
        if (target.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "compass:server:usage")))
            return
        }
        if (target.equals("clear", ignoreCase = true) || target.equals("off", ignoreCase = true)) {
            session.send(ServerMessage.CompassUpdate(0f, 0f, 0f, active = false))
            session.send(ServerMessage.Notification(i18n.t(lang, "compass:server:cleared")))
            return
        }

        val parts = target.split(Regex("\\s+"))
        if (parts.size == 3) {
            val x = parts[0].toFloatOrNull()
            val y = parts[1].toFloatOrNull()
            val z = parts[2].toFloatOrNull()
            if (x != null && y != null && z != null) {
                session.send(ServerMessage.CompassUpdate(x, y, z, active = true))
                session.send(
                    ServerMessage.Notification(
                        i18n.t(
                            lang,
                            "compass:server:set",
                            x.toInt().toString(),
                            y.toInt().toString(),
                            z.toInt().toString())))
                return
            }
        }

        val point = context.namedPoints()[target]
        if (point == null) {
            session.send(
                ServerMessage.Notification(i18n.t(lang, "compass:server:not_found", target)))
            return
        }
        session.send(ServerMessage.CompassUpdate(point.x, point.y, point.z, target, active = true))
        session.send(ServerMessage.Notification(i18n.t(lang, "compass:server:set_named", target)))
    }
}
