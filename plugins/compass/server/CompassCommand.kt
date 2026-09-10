package org.micoli.micraft.plugins.compass

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.Completion
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.CompassTargetState
import org.micoli.micraft.protocol.ServerMessage

class CompassCommand : PluginCommand {
    override val id: UUID = UUID.fromString("f1a2b3c4-d5e6-4788-9a0b-1c2d3e4f5a6b")
    override val name = "compass"
    override val command = "/compass"
    override val description = "Points the compass widget at coordinates or a named point."
    override val usage = "/compass <x y z | pointName | toggle | clear>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArgRich(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext,
    ): List<Completion> {
        if (argIndex != 0) return emptyList()
        val names = context.namedPoints().keys.toList() + listOf("toggle", "clear")
        return names.filter { it.contains(partial, ignoreCase = true) }.map { Completion(it) }
    }

    /** Stores the target on [PlayerSession.state] (so it persists) and echoes it to the client. */
    private suspend fun apply(session: PlayerSession, state: CompassTargetState?) {
        session.state = session.state.copy(compassTarget = state)
        session.send(
            if (state == null) ServerMessage.CompassUpdate(0f, 0f, 0f, hasTarget = false)
            else
                ServerMessage.CompassUpdate(
                    state.x, state.y, state.z, state.label, active = state.visible))
    }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val arg = args.trim()

        if (arg.isBlank()) {
            session.send(ServerMessage.Notification(i18n.t(lang, "compass:server:usage")))
            return
        }

        if (arg.equals("clear", ignoreCase = true) || arg.equals("off", ignoreCase = true)) {
            apply(session, null)
            session.send(ServerMessage.Notification(i18n.t(lang, "compass:server:cleared")))
            return
        }

        if (arg.equals("toggle", ignoreCase = true)) {
            val current = session.state.compassTarget
            if (current == null) {
                session.send(ServerMessage.Notification(i18n.t(lang, "compass:server:no_target")))
                return
            }
            apply(session, current.copy(visible = !current.visible))
            return
        }

        val parts = arg.split(Regex("\\s+"))
        if (parts.size == 3) {
            val x = parts[0].toFloatOrNull()
            val y = parts[1].toFloatOrNull()
            val z = parts[2].toFloatOrNull()
            if (x != null && y != null && z != null) {
                apply(session, CompassTargetState(x, y, z))
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

        val point = context.namedPoints()[arg]
        if (point == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "compass:server:not_found", arg)))
            return
        }
        apply(session, CompassTargetState(point.x, point.y, point.z, arg))
        session.send(ServerMessage.Notification(i18n.t(lang, "compass:server:set_named", arg)))
    }
}
