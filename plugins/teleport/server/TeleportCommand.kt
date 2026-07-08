package org.micoli.micraft.plugins.teleport

import java.util.UUID
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.PluginCommand
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage

class TeleportCommand : PluginCommand {
    override val id: UUID = UUID.fromString("664abb28-f6c2-45fe-91de-da3c38562fbc")
    override val name = "teleport"
    override val command = "/teleport"
    override val description = "Teleports you to the given coordinates."
    override val usage = "/teleport <x> <y> <z>  |  /teleport <playerName>"

    override val autocompleteArgs = listOf(0)

    override suspend fun completeArg(
        argIndex: Int,
        partial: String,
        session: PlayerSession?,
        context: CommandContext
    ): List<String> =
        context
            .sessions()
            .map { it.state.name }
            .filter { it.startsWith(partial, ignoreCase = true) }

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val parts = args.trim().split(Regex("\\s+|,"))
        if (parts.size == 1 && parts[0].isNotEmpty() && parts[0].toFloatOrNull() == null) {
            val target = context.sessions().find { it.state.name == parts[0] }
            if (target == null) {
                session.send(
                    ServerMessage.Notification(i18n.t(lang, "teleport:server:not_found", parts[0])))
                return
            }
            val dest = safeTeleportPos(context.world, target.state.pos)
            session.state = session.state.copy(pos = dest)
            session.vy = 0f
            session.send(ServerMessage.PlayerUpdate(session.state))
            session.send(
                ServerMessage.Notification(
                    i18n.t(
                        lang,
                        "teleport:server:done",
                        dest.x.toInt(),
                        dest.y.toInt(),
                        dest.z.toInt())))
            return
        }
        val x = parts.getOrNull(0)?.toFloatOrNull()
        val y = parts.getOrNull(1)?.toFloatOrNull()
        val z = parts.getOrNull(2)?.toFloatOrNull()
        if (x == null || y == null || z == null) {
            session.send(ServerMessage.Notification(i18n.t(lang, "teleport:server:usage")))
            return
        }
        val dest = safeTeleportPos(context.world, Vec3(x, y, z))
        session.state = session.state.copy(pos = dest)
        session.vy = 0f
        session.send(ServerMessage.PlayerUpdate(session.state))
        session.send(
            ServerMessage.Notification(
                i18n.t(
                    lang, "teleport:server:done", dest.x.toInt(), dest.y.toInt(), dest.z.toInt())))
    }
}
