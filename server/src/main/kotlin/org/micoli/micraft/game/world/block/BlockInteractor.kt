package org.micoli.micraft.game.world.block

import kotlin.math.sqrt
import org.micoli.micraft.game.MAX_INTERACTION_DISTANCE
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.instance.InstanceRegistry
import org.micoli.micraft.game.world.rail.RailConnection
import org.micoli.micraft.game.world.rail.RailNetworkRegistry
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(BlockInteractor::class.java)

/**
 * Right-click interaction with a placed block (see [ClientMessage.BlockInteract]) — generic
 * mechanism dispatched by block type, mirroring [BlockPlacer]/[BlockBreaker]'s validation shape.
 * First (and currently only) handler: toggling a switch/junction rail block's active branch.
 * Extensible to future stateful blocks (doors, levers) without a protocol change.
 */
class BlockInteractor(
    private val world: WorldState,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val instanceRegistry: InstanceRegistry? = null,
    private val railNetworkRegistry: RailNetworkRegistry? = null,
) {
    suspend fun handleInteract(session: PlayerSession, intent: ClientMessage.BlockInteract) {
        val pos = intent.pos

        if (instanceRegistry?.zoneAt(pos.x, pos.y, pos.z) != null) {
            log.debug("BlockInteract rejected: pos={} is inside a protected zone", pos)
            return
        }

        val eyeY = session.state.pos.y + session.state.stance.eyeOffset
        val dist =
            sqrt(
                ((pos.x + 0.5f - session.state.pos.x) * (pos.x + 0.5f - session.state.pos.x) +
                        (pos.y + 0.5f - eyeY) * (pos.y + 0.5f - eyeY) +
                        (pos.z + 0.5f - session.state.pos.z) * (pos.z + 0.5f - session.state.pos.z))
                    .toDouble())
        if (dist > MAX_INTERACTION_DISTANCE) {
            log.debug(
                "BlockInteract rejected: dist={} > {}",
                "%.2f".format(dist),
                MAX_INTERACTION_DISTANCE)
            return
        }

        val blockType = world.getBlock(pos.x, pos.y, pos.z)
        if (!RailConnection.isJunction(blockType)) {
            log.debug("BlockInteract rejected: {} at {} has no switch to toggle", blockType, pos)
            return
        }

        val state = world.getBlockState(pos.x, pos.y, pos.z)
        val branches = RailConnection.branchCount(blockType)
        val extraState = world.getExtraState(pos.x, pos.y, pos.z)
        val nextBranch = (BlockState.extra(extraState) + 1) % branches
        val change = BlockChange(pos, blockType, state, BlockState.packExtra(nextBranch))
        world.applyChange(change)
        railNetworkRegistry?.invalidate(pos)
        broadcast(ServerMessage.WorldUpdate(listOf(change)))
        log.debug("BlockInteract: {} at {} switched to branch {}", blockType, pos, nextBranch)
    }
}
