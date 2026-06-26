package org.micoli.micraft.command

import java.util.UUID
import org.micoli.micraft.CommandContext
import org.micoli.micraft.CommandHandler
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.BlockPos
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.isSolid

class WaterCommand : CommandHandler {
    override val id: UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    override val command = "/water"
    override val description =
        "Place a water source on the solid block you are looking at (or x y z). (admin)"
    override val usage = "/water [x y z]"
    override val options = emptyList<String>()

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val liquidManager = context.liquidManager
        if (liquidManager == null) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "water:server:unavailable")))
            return
        }

        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size != 3) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "water:server:no_solid_target")))
            return
        }

        val x = parts[0].toIntOrNull()
        val y = parts[1].toIntOrNull()
        val z = parts[2].toIntOrNull()
        if (x == null || y == null || z == null) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "water:server:usage")))
            return
        }

        if (y !in WorldConstants.WORLD_MIN_Y..WorldConstants.WORLD_MAX_Y) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "water:server:out_of_bounds")))
            return
        }

        val blockBelow = context.world.getBlock(x, y - 1, z)
        if (!blockBelow.isSolid) {
            session.send(
                ServerMessage.Notification(context.i18n.t(lang, "water:server:no_solid_below")))
            return
        }

        val existing = context.world.getBlock(x, y, z)
        if (existing != BlockType.AIR) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "water:server:not_air")))
            return
        }

        val pos = BlockPos(x, y, z)
        val change = BlockChange(pos, BlockType.WATER)
        context.world.applyChange(change)
        context.broadcast(ServerMessage.WorldUpdate(listOf(change)))
        liquidManager.activate(pos, 0)

        session.send(
            ServerMessage.Notification(context.i18n.t(lang, "water:server:placed", x, y, z)))
    }
}
