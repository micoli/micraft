package org.micoli.micraft.command

import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession

class TeleportCommand : CommandHandler {
    override val command = "/teleport"
    override val description = "Teleports you to the given coordinates."
    override val usage = "/teleport <x> <y> <z>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val i18n = context.i18n
        val parts = args.trim().split(Regex("\\s+|,"))
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
        session.send(ServerMessage.Notification(i18n.t(lang, "teleport:server:done", dest.x.toInt(), dest.y.toInt(), dest.z.toInt())))
    }
}
