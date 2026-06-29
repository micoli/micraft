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
import org.micoli.micraft.world.WorldState
import org.micoli.micraft.world.isLiquid

private const val MAX_PUMP_BLOCKS = 10_000

class PumpCommand : CommandHandler {
    override val id: UUID = UUID.fromString("b7e2a1f3-9c4d-4e5b-8f6a-2d3c1e0b9a7f")
    override val command = "/pump"
    override val permission = "admin"
    override val description = "Remove all connected liquid blocks in sight."
    override val usage = "/pump"

    override suspend fun execute(session: PlayerSession, args: String, context: CommandContext) {
        val lang = session.state.language

        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val target: BlockPos? =
            if (parts.size == 3) {
                val x = parts[0].toIntOrNull()
                val y = parts[1].toIntOrNull()
                val z = parts[2].toIntOrNull()
                if (x != null && y != null && z != null) BlockPos(x, y, z) else null
            } else null

        if (target == null || !context.world.getBlock(target.x, target.y, target.z).isLiquid) {
            session.send(ServerMessage.Notification(context.i18n.t(lang, "pump:server:no_target")))
            return
        }

        val collected = collectConnectedLiquids(target, context.world)
        val changes = collected.map { BlockChange(it, BlockType.AIR) }
        changes.forEach { context.world.applyChange(it) }
        context.liquidManager?.deactivateAll(collected)
        context.broadcast(ServerMessage.WorldUpdate(changes))
        session.send(
            ServerMessage.Notification(context.i18n.t(lang, "pump:server:done", collected.size)))
    }

    private fun collectConnectedLiquids(start: BlockPos, world: WorldState): List<BlockPos> {
        val result = mutableListOf<BlockPos>()
        val visited = mutableSetOf(start)
        val queue = ArrayDeque<BlockPos>()
        queue.add(start)

        while (queue.isNotEmpty() && result.size < MAX_PUMP_BLOCKS) {
            val pos = queue.removeFirst()
            result.add(pos)
            for ((dx, dy, dz) in NEIGHBORS) {
                val nx = pos.x + dx
                val ny = pos.y + dy
                val nz = pos.z + dz
                if (ny < WorldConstants.WORLD_MIN_Y || ny > WorldConstants.WORLD_MAX_Y) continue
                val neighbor = BlockPos(nx, ny, nz)
                if (neighbor in visited) continue
                visited.add(neighbor)
                if (world.getBlock(nx, ny, nz).isLiquid) queue.add(neighbor)
            }
        }
        return result
    }

    companion object {
        private val NEIGHBORS =
            listOf(
                Triple(1, 0, 0),
                Triple(-1, 0, 0),
                Triple(0, 1, 0),
                Triple(0, -1, 0),
                Triple(0, 0, 1),
                Triple(0, 0, -1),
            )
    }
}
