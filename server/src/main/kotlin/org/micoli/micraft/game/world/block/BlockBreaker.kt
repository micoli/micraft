package org.micoli.micraft.game.world.block

import kotlin.math.sqrt
import org.micoli.micraft.game.MAX_INTERACTION_DISTANCE
import org.micoli.micraft.game.session.PlayerSession
import org.micoli.micraft.game.session.WorldActionRecord
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockRegistry
import org.micoli.micraft.game.world.BlockState
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.game.world.WorldItemManager
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.game.world.liquid.LiquidManager
import org.micoli.micraft.player.eyeOffset
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ClientMessage
import org.micoli.micraft.protocol.EntityRemoveAt
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val blockBreakerLog = LoggerFactory.getLogger(BlockBreaker::class.java)
private const val MAX_UNDO_HISTORY = 20

private data class BlockBreakEntry(val blockType: BlockType, var ticks: Int)

class BlockBreaker(
    private val world: WorldState,
    private val broadcast: suspend (ServerMessage) -> Unit,
    private val worldItems: WorldItemManager,
    private val liquidManager: LiquidManager? = null,
    private val bufferSize: Int = 1000,
) {
    private val blockProgress = LinkedHashMap<BlockPos, BlockBreakEntry>()

    fun handleStart(session: PlayerSession, intent: ClientMessage.BlockBreakStart) {
        val rawBp = intent.pos
        val block = world.getBlock(rawBp.x, rawBp.y, rawBp.z)
        val eyeY = session.state.pos.y + session.state.stance.eyeOffset
        val dist =
            sqrt(
                ((rawBp.x + 0.5f - session.state.pos.x) * (rawBp.x + 0.5f - session.state.pos.x) +
                        (rawBp.y + 0.5f - eyeY) * (rawBp.y + 0.5f - eyeY) +
                        (rawBp.z + 0.5f - session.state.pos.z) *
                            (rawBp.z + 0.5f - session.state.pos.z))
                    .toDouble())
        val hasEntity = world.hasEntityAt(rawBp.x, rawBp.y, rawBp.z)
        blockBreakerLog.debug(
            "BlockBreakStart pos={} block={} dist={}", rawBp, block, "%.2f".format(dist))
        if (dist <= MAX_INTERACTION_DISTANCE &&
            (hasEntity || (block.hardness > 0f && block.hardness != Float.MAX_VALUE))) {
            // Resolve satellite → master so blockProgress key is consistent
            val masterPos = world.getEntityMasterWorldPos(rawBp.x, rawBp.y, rawBp.z)
            session.breakTarget = masterPos ?: rawBp
        }
    }

    fun handleStop(session: PlayerSession) {
        session.breakTarget = null
    }

    private fun activateAdjacentLiquids(pos: BlockPos) {
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
                val neighborPos = BlockPos(nx, ny, nz)
                lm.activate(neighborPos, lm.getFlowDistance(neighborPos))
            }
        }
    }

    suspend fun tick(session: PlayerSession) {
        val bt = session.breakTarget ?: return
        // For entity satellite cells, resolve to master block for hardness
        val masterPos = world.getEntityMasterWorldPos(bt.x, bt.y, bt.z)
        val effectivePos = masterPos ?: bt
        val topmostFractional =
            if (masterPos != null)
                world.getTopmostFractionalEntityAt(masterPos.x, masterPos.y, masterPos.z)
            else null
        val lastXZFractional =
            if (masterPos != null && topmostFractional == null)
                world.getLastXZFractionalEntityAt(masterPos.x, masterPos.y, masterPos.z)
            else null
        val blockAtPos = world.getBlock(effectivePos.x, effectivePos.y, effectivePos.z)
        // When block type is AIR but a fractional entity (yOffset>0 only) is present, use entity
        // type for hardness
        val block =
            if (blockAtPos == BlockType.AIR && topmostFractional != null) topmostFractional.type
            else blockAtPos
        // Read before the block is cleared: drives which color variant the drops use.
        val brokenColorIndex =
            (topmostFractional ?: lastXZFractional)?.colorIndex
                ?: BlockState.colorIndex(
                    world.getState(effectivePos.x, effectivePos.y, effectivePos.z))
        if (block.hardness == 0f || block.hardness == Float.MAX_VALUE) {
            session.breakTarget = null
            blockProgress.remove(bt)
            return
        }
        val entry = blockProgress[bt]
        val current: BlockBreakEntry
        if (entry == null || entry.blockType != block) {
            if (entry == null && blockProgress.size >= bufferSize) {
                blockProgress.remove(blockProgress.keys.first())
            }
            current = BlockBreakEntry(block, 0)
            blockProgress[bt] = current
        } else {
            current = entry
        }
        current.ticks++
        if (current.ticks.toFloat() >= block.hardness) {
            if (masterPos != null) {
                if (topmostFractional != null) {
                    // Y-fractional entity (plate): remove only the topmost plate
                    val spec = EntityRemoveAt(masterPos, topmostFractional.yOffset)
                    world.applyEntityRemoveAt(spec)
                    val changes = mutableListOf<BlockChange>()
                    // If topmost was yOffset=0 AND no more plates remain → clear block type
                    val remaining =
                        world.getFractionalYOffsetsAt(masterPos.x, masterPos.y, masterPos.z)
                    if (topmostFractional.yOffset == 0 && remaining.isEmpty()) {
                        val c = BlockChange(masterPos, BlockType.AIR)
                        world.applyChange(c)
                        changes.add(c)
                    }
                    broadcast(ServerMessage.WorldUpdate(changes, entityRemovesAt = listOf(spec)))
                } else if (lastXZFractional != null) {
                    // XZ-fractional entity (arch): remove the last-placed slot
                    val spec =
                        EntityRemoveAt(
                            masterPos, 0, lastXZFractional.xOffset, lastXZFractional.zOffset)
                    world.applyEntityRemoveAt(spec)
                    val changes = mutableListOf<BlockChange>()
                    // If no more XZ-fractional entities remain at this position → clear block type
                    val remaining = world.getXZOffsetsAt(masterPos.x, masterPos.y, masterPos.z)
                    if (remaining.isEmpty()) {
                        val entityDef =
                            BlockRegistry.get(world.getBlock(masterPos.x, masterPos.y, masterPos.z))
                        val sizeX =
                            kotlin.math
                                .ceil(entityDef.brickSize.getOrElse(0) { 1f })
                                .toInt()
                                .coerceAtLeast(1)
                        val sizeY =
                            kotlin.math
                                .ceil(entityDef.brickSize.getOrElse(1) { 1f })
                                .toInt()
                                .coerceAtLeast(1)
                        val sizeZ =
                            kotlin.math
                                .ceil(entityDef.brickSize.getOrElse(2) { 1f })
                                .toInt()
                                .coerceAtLeast(1)
                        for (dx in 0 until sizeX) for (dy in 0 until sizeY) for (dz in
                            0 until sizeZ) {
                            val cp = BlockPos(masterPos.x + dx, masterPos.y + dy, masterPos.z + dz)
                            val existingBlock = world.getBlock(cp.x, cp.y, cp.z)
                            if (existingBlock != BlockType.AIR) {
                                val c = BlockChange(cp, BlockType.AIR)
                                world.applyChange(c)
                                changes.add(c)
                            }
                        }
                    }
                    broadcast(ServerMessage.WorldUpdate(changes, entityRemovesAt = listOf(spec)))
                } else {
                    // Regular multi-cell entity: remove entire entity + all cell block types
                    val entityDef =
                        BlockRegistry.get(world.getBlock(masterPos.x, masterPos.y, masterPos.z))
                    val sizeX =
                        kotlin.math
                            .ceil(entityDef.brickSize.getOrElse(0) { 1f })
                            .toInt()
                            .coerceAtLeast(1)
                    val sizeY =
                        kotlin.math
                            .ceil(entityDef.brickSize.getOrElse(1) { 1f })
                            .toInt()
                            .coerceAtLeast(1)
                    val sizeZ =
                        kotlin.math
                            .ceil(entityDef.brickSize.getOrElse(2) { 1f })
                            .toInt()
                            .coerceAtLeast(1)
                    val changes = mutableListOf<BlockChange>()
                    for (dx in 0 until sizeX) for (dy in 0 until sizeY) for (dz in 0 until sizeZ) {
                        val cp = BlockPos(masterPos.x + dx, masterPos.y + dy, masterPos.z + dz)
                        val existingBlock = world.getBlock(cp.x, cp.y, cp.z)
                        if (existingBlock != BlockType.AIR) {
                            val c = BlockChange(cp, BlockType.AIR)
                            world.applyChange(c)
                            changes.add(c)
                        }
                    }
                    world.applyEntityRemove(masterPos)
                    broadcast(ServerMessage.WorldUpdate(changes, entityRemoves = listOf(masterPos)))
                }
            } else {
                val change = BlockChange(bt, BlockType.AIR)
                world.applyChange(change)
                broadcast(ServerMessage.WorldUpdate(listOf(change)))
            }
            activateAdjacentLiquids(bt)
            val spawned = worldItems.spawnDrops(bt, block, brokenColorIndex)
            session.actionHistory.addLast(WorldActionRecord.Break(bt, block, spawned))
            if (session.actionHistory.size > MAX_UNDO_HISTORY) session.actionHistory.removeFirst()
            session.breakTarget = null
            blockProgress.remove(bt)
        } else {
            session.send(ServerMessage.BlockBreakProgress(bt, current.ticks, block.hardness))
        }
    }
}
