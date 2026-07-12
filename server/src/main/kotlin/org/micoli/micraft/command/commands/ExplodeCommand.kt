package org.micoli.micraft.command.commands

import java.util.UUID
import kotlin.math.floor
import org.micoli.micraft.command.CommandContext
import org.micoli.micraft.command.CommandHandler
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage

private const val MAX_RADIUS = 32

class ExplodeCommand : CommandHandler {
    override val id: UUID = UUID.fromString("570782cd-ac20-413c-ad7b-9f7fcb312df0")
    override val name = "explode"
    override val permission = "admin"
    override val description = "Destroy all blocks in a sphere around the player."
    override val usage = "/explode <radius>"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language
        val radius = args.trim().toIntOrNull()
        if (radius == null || radius < 1) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "explode:server:usage")))
            return
        }
        if (radius > MAX_RADIUS) {
            session.send(
                ServerMessage.Notification(
                    context.i18n.t(lang, "explode:server:too_large", MAX_RADIUS)))
            return
        }

        val cx = floor(session.state.pos.x.toDouble()).toInt()
        val cy = floor(session.state.pos.y.toDouble()).toInt()
        val cz = floor(session.state.pos.z.toDouble()).toInt()
        val r2 = radius * radius

        val changes = mutableListOf<BlockChange>()
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue
                    val bx = cx + dx
                    val by = cy + dy
                    val bz = cz + dz
                    if (by < WorldConstants.WORLD_MIN_Y || by > WorldConstants.WORLD_MAX_Y) continue
                    val block = context.world.getBlock(bx, by, bz)
                    if (block == BlockType.AIR) continue
                    changes.add(BlockChange(BlockPos(bx, by, bz), BlockType.AIR))
                }
            }
        }

        changes.forEach { context.world.applyChange(it) }
        context.broadcast(ServerMessage.WorldUpdate(changes))
        session.send(
            ServerMessage.Notification(context.i18n.t(lang, "explode:server:done", changes.size)))
    }
}
