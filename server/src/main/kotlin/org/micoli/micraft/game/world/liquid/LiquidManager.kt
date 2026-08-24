package org.micoli.micraft.game.world.liquid

import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(LiquidManager::class.java)
private const val MAX_FLOW_DISTANCE = 40

class LiquidManager(private val world: WorldState) {
    private val activeLiquids: MutableSet<BlockPos> = ConcurrentHashMap.newKeySet()
    private val flowDistance: ConcurrentHashMap<BlockPos, Int> = ConcurrentHashMap()
    private val pendingTicks: ConcurrentHashMap<BlockPos, Int> = ConcurrentHashMap()

    fun activate(pos: BlockPos, distance: Int = 0) {
        if (pos.y < WorldConstants.WORLD_MIN_Y || pos.y > WorldConstants.WORLD_MAX_Y) return
        activeLiquids.add(pos)
        flowDistance[pos] = distance
        pendingTicks[pos] = 0
    }

    fun getFlowDistance(pos: BlockPos): Int = flowDistance[pos] ?: 0

    suspend fun tick(broadcast: suspend (ServerMessage) -> Unit) {
        if (activeLiquids.isEmpty()) return

        val toProcess =
            activeLiquids.filter { pos ->
                val remaining = pendingTicks.merge(pos, -1) { a, b -> a + b } ?: -1
                remaining <= 0
            }
        if (toProcess.isEmpty()) return

        val changes = mutableListOf<BlockChange>()

        for (pos in toProcess) {
            val block = world.getBlock(pos.x, pos.y, pos.z)
            if (!block.isLiquid) {
                activeLiquids.remove(pos)
                flowDistance.remove(pos)
                pendingTicks.remove(pos)
                continue
            }
            val visc = block.viscosity

            val belowY = pos.y - 1
            if (belowY >= WorldConstants.WORLD_MIN_Y) {
                val below = world.getBlock(pos.x, belowY, pos.z)
                if (below == BlockType.AIR) {
                    val belowPos = BlockPos(pos.x, belowY, pos.z)
                    val change = BlockChange(belowPos, block)
                    world.applyChange(change)
                    changes.add(change)
                    flowDistance[belowPos] = 0
                    pendingTicks[belowPos] = visc
                    activeLiquids.add(belowPos)
                    // Keep current block active — it may continue falling
                    pendingTicks[pos] = visc
                    continue
                }
            }

            // Cannot fall — spread horizontally if within flow distance
            val dist = flowDistance[pos] ?: 0
            for ((dx, dz) in HORIZONTAL_NEIGHBORS) {
                val nx = pos.x + dx
                val nz = pos.z + dz
                val neighborPos = BlockPos(nx, pos.y, nz)
                val neighborBlock = world.getBlock(nx, pos.y, nz)
                if (neighborBlock == BlockType.AIR && dist < MAX_FLOW_DISTANCE) {
                    val change = BlockChange(neighborPos, block)
                    world.applyChange(change)
                    changes.add(change)
                    flowDistance[neighborPos] = dist + 1
                    pendingTicks[neighborPos] = visc
                    activeLiquids.add(neighborPos)
                } else if (neighborBlock.isLiquid) {
                    // Same-tick race: this block may have been filled via a longer path;
                    // propagate shorter flow distance so it can keep spreading.
                    val existingDist = flowDistance[neighborPos] ?: MAX_FLOW_DISTANCE
                    if (dist + 1 < existingDist) {
                        flowDistance[neighborPos] = dist + 1
                        activeLiquids.add(neighborPos)
                    }
                }
            }
            // Block is settled — remove from active set
            activeLiquids.remove(pos)
            pendingTicks.remove(pos)
        }

        if (changes.isNotEmpty()) {
            log.debug("Liquid propagation: {} block changes", changes.size)
            broadcast(ServerMessage.WorldUpdate(changes))
        }
    }

    fun activeLiquidCount(): Int = activeLiquids.size

    fun pendingTickCount(): Int = pendingTicks.size

    fun deactivate(pos: BlockPos) {
        activeLiquids.remove(pos)
        flowDistance.remove(pos)
        pendingTicks.remove(pos)
    }

    fun deactivateAll(positions: Collection<BlockPos>) = positions.forEach { deactivate(it) }

    companion object {
        private val HORIZONTAL_NEIGHBORS = listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))
    }
}
