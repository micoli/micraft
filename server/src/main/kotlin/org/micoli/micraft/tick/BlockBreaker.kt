package org.micoli.micraft.tick

import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.micoli.micraft.session.PlayerSession
import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.WorldItemManager
import org.micoli.micraft.world.WorldState
import org.micoli.micraft.world.hardness
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(BlockBreaker::class.java)

class BlockBreaker(
    private val world: WorldState,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val worldItems: WorldItemManager,
) {
    fun handleStart(session: PlayerSession, intent: ClientMessage.BlockBreakStart) {
        val bp = intent.pos
        val block = world.getBlock(bp.x, bp.y, bp.z)
        val eyeY = session.state.pos.y + session.state.stance.eyeOffset
        val dist = kotlin.math.sqrt(
            ((bp.x + 0.5f - session.state.pos.x) * (bp.x + 0.5f - session.state.pos.x) +
             (bp.y + 0.5f - eyeY) * (bp.y + 0.5f - eyeY) +
             (bp.z + 0.5f - session.state.pos.z) * (bp.z + 0.5f - session.state.pos.z)).toDouble()
        )
        log.debug("BlockBreakStart pos=$bp block=$block dist=${"%.2f".format(dist)}")
        if (dist <= 6.0 && block != BlockType.AIR && block != BlockType.BEDROCK) {
            session.breakTarget = bp
            session.breakProgress = 0
        }
    }

    fun handleStop(session: PlayerSession) {
        session.breakTarget = null
        session.breakProgress = 0
    }

    suspend fun tick(session: PlayerSession) {
        val bt = session.breakTarget ?: return
        val block = world.getBlock(bt.x, bt.y, bt.z)
        if (block == BlockType.AIR || block == BlockType.BEDROCK) {
            session.breakTarget = null
            session.breakProgress = 0
            return
        }
        session.breakProgress++
        if (session.breakProgress >= block.hardness) {
            val change = BlockChange(bt, BlockType.AIR)
            world.applyChange(change)
            broadcast(ServerMessage.WorldUpdate(listOf(change)))
            worldItems.spawnDrops(bt, block)
            session.breakTarget = null
            session.breakProgress = 0
        } else {
            session.send(ServerMessage.BlockBreakProgress(bt, session.breakProgress, block.hardness))
        }
    }
}
