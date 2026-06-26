package org.micoli.micraft.tick

import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.session.WorldActionRecord
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.WorldConstants
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldState
import org.micoli.micraft.world.hardness
import org.micoli.micraft.world.isLiquid
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(BlockBreaker::class.java)
private const val MAX_UNDO_HISTORY = 20

class BlockBreaker(
    private val world: WorldState,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val worldItems: WorldItemManager,
    private val liquidManager: LiquidManager? = null,
) {
    fun handleStart(session: PlayerSession, intent: ClientMessage.BlockBreakStart) {
        val bp = intent.pos
        val block = world.getBlock(bp.x, bp.y, bp.z)
        val eyeY = session.state.pos.y + session.state.stance.eyeOffset
        val dist =
            kotlin.math.sqrt(
                ((bp.x + 0.5f - session.state.pos.x) * (bp.x + 0.5f - session.state.pos.x) +
                        (bp.y + 0.5f - eyeY) * (bp.y + 0.5f - eyeY) +
                        (bp.z + 0.5f - session.state.pos.z) * (bp.z + 0.5f - session.state.pos.z))
                    .toDouble())
        log.debug("BlockBreakStart pos={} block={} dist={}", bp, block, "%.2f".format(dist))
        if (dist <= org.micoli.micraft.MAX_INTERACTION_DISTANCE &&
            block.hardness > 0 &&
            block.hardness != Int.MAX_VALUE) {
            session.breakTarget = bp
            session.breakProgress = 0
        }
    }

    fun handleStop(session: PlayerSession) {
        session.breakTarget = null
        session.breakProgress = 0
    }

    private fun activateAdjacentLiquids(pos: org.micoli.micraft.world.BlockPos) {
        val lm = liquidManager ?: return
        val neighbors =
            listOf(
                Triple(pos.x + 1, pos.y, pos.z),
                Triple(pos.x - 1, pos.y, pos.z),
                Triple(pos.x, pos.y, pos.z + 1),
                Triple(pos.x, pos.y, pos.z - 1),
                Triple(pos.x, pos.y + 1, pos.z),
                Triple(pos.x, pos.y - 1, pos.z),
            )
        for ((nx, ny, nz) in neighbors) {
            if (ny < WorldConstants.WORLD_MIN_Y || ny > WorldConstants.WORLD_MAX_Y) continue
            val neighbor = world.getBlock(nx, ny, nz)
            if (neighbor.isLiquid) {
                val neighborPos = org.micoli.micraft.world.BlockPos(nx, ny, nz)
                lm.activate(neighborPos, lm.getFlowDistance(neighborPos))
            }
        }
    }

    suspend fun tick(session: PlayerSession) {
        val bt = session.breakTarget ?: return
        val block = world.getBlock(bt.x, bt.y, bt.z)
        if (block.hardness == 0 || block.hardness == Int.MAX_VALUE) {
            session.breakTarget = null
            session.breakProgress = 0
            return
        }
        session.breakProgress++
        if (session.breakProgress >= block.hardness) {
            val change = BlockChange(bt, BlockType.AIR)
            world.applyChange(change)
            broadcast(ServerMessage.WorldUpdate(listOf(change)))
            activateAdjacentLiquids(bt)
            val spawned = worldItems.spawnDrops(bt, block)
            session.actionHistory.addLast(WorldActionRecord.Break(bt, block, spawned))
            if (session.actionHistory.size > MAX_UNDO_HISTORY) session.actionHistory.removeFirst()
            session.breakTarget = null
            session.breakProgress = 0
        } else {
            session.send(
                ServerMessage.BlockBreakProgress(bt, session.breakProgress, block.hardness))
        }
    }
}
