package org.micoli.micraft.game

import org.micoli.micraft.ChunkManager
import org.micoli.micraft.LocalPlayerController
import org.micoli.micraft.babylon.jsEditModeUpdate
import org.micoli.micraft.babylon.jsGodModeUpdate
import org.micoli.micraft.babylon.jsWalletUpdate
import org.micoli.micraft.game.world.BlockEntity
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos
import org.micoli.micraft.game.world.WorldConstants
import org.micoli.micraft.protocol.ServerMessage

/** Chunk streaming and world block/entity diffs, plus the misc per-chunk state broadcasts. */
class ChunkWorldHandler(
    private val chunkManager: ChunkManager,
    private val localController: LocalPlayerController,
    private val e2eSession: String,
) : ServerMessageHandler {
    /** Mirrors the latest [ServerMessage.WorldUpdate] as JSON for the e2e snapshot, e2e-only. */
    var lastWorldUpdateJson: String = "null"
        private set

    override fun handle(msg: ServerMessage) =
        when (msg) {
            is ServerMessage.ChunkData -> {
                chunkManager.setGrassTints(msg.pos, msg.grassTints)
                chunkManager.enqueueChunk(
                    Chunk.decodeWire(
                        msg.pos,
                        msg.topY,
                        msg.wireBlocks,
                        msg.wireStates.takeIf { it.isNotEmpty() },
                        msg.wireExtraStates.takeIf { it.isNotEmpty() },
                        msg.entities),
                    msg.topY)
            }
            is ServerMessage.ShadersUpdate -> chunkManager.setShadersEnabled(msg.enabled)
            is ServerMessage.LightBoostUpdate -> localController.lightBoostEnabled = msg.enabled
            is ServerMessage.GodModeUpdate -> jsGodModeUpdate(msg.enabled)
            is ServerMessage.EditModeUpdate -> jsEditModeUpdate(msg.mode.name.lowercase())
            is ServerMessage.WalletUpdate -> jsWalletUpdate(msg.copper)
            is ServerMessage.WorldUpdate -> handleWorldUpdate(msg)
            else -> Unit
        }

    private fun handleWorldUpdate(msg: ServerMessage.WorldUpdate) {
        if (e2eSession.isNotEmpty()) {
            lastWorldUpdateJson =
                msg.changes.joinToString(prefix = "[", postfix = "]") { c ->
                    """{"x":${c.pos.x},"y":${c.pos.y},"z":${c.pos.z},"block":"${c.type.id}"}"""
                }
        }
        // Collect affected chunk positions for re-enqueue after applying all changes
        val affectedChunks = mutableMapOf<ChunkPos, Pair<Chunk, Int>>()

        msg.changes.forEach { change ->
            val cx = change.pos.x.floorDiv(WorldConstants.CHUNK_SIZE)
            val cz = change.pos.z.floorDiv(WorldConstants.CHUNK_SIZE)
            val cp = ChunkPos(cx, cz)
            val (existing, existingTopY) =
                affectedChunks[cp] ?: chunkManager.chunkData[cp] ?: return@forEach
            val lx = change.pos.x - cx * WorldConstants.CHUNK_SIZE
            val lz = change.pos.z - cz * WorldConstants.CHUNK_SIZE
            val updated =
                existing.withBlock(
                    lx, change.pos.y, lz, change.type, change.state, change.extraState)
            val newTopY =
                if (change.type != BlockType.AIR) maxOf(existingTopY, change.pos.y)
                else existingTopY
            affectedChunks[cp] = Pair(updated, newTopY)
            if (change.type == BlockType.AIR) localController.onBlockBroken(change.pos)
        }

        msg.entityAdds.forEach { proto ->
            val cx = proto.worldX.floorDiv(WorldConstants.CHUNK_SIZE)
            val cz = proto.worldZ.floorDiv(WorldConstants.CHUNK_SIZE)
            val cp = ChunkPos(cx, cz)
            val (existing, topY) =
                affectedChunks[cp] ?: chunkManager.chunkData[cp] ?: return@forEach
            val localX = proto.worldX - cx * WorldConstants.CHUNK_SIZE
            val localZ = proto.worldZ - cz * WorldConstants.CHUNK_SIZE
            val masterIdx = Chunk.index(localX, proto.worldY, localZ)
            val entity =
                BlockEntity(
                    masterIdx = masterIdx,
                    type = BlockType(proto.type),
                    sizeX = proto.sizeX,
                    sizeY = proto.sizeY,
                    sizeZ = proto.sizeZ,
                    rotation = proto.rotation,
                    yOffset = proto.yOffset,
                    xOffset = proto.xOffset,
                    zOffset = proto.zOffset,
                    colorIndex = proto.colorIndex,
                )
            affectedChunks[cp] = Pair(existing.addEntity(entity), topY)
        }

        msg.entityRemoves.forEach { masterWorldPos ->
            val cx = masterWorldPos.x.floorDiv(WorldConstants.CHUNK_SIZE)
            val cz = masterWorldPos.z.floorDiv(WorldConstants.CHUNK_SIZE)
            val cp = ChunkPos(cx, cz)
            val (existing, topY) =
                affectedChunks[cp] ?: chunkManager.chunkData[cp] ?: return@forEach
            val localX = masterWorldPos.x - cx * WorldConstants.CHUNK_SIZE
            val localZ = masterWorldPos.z - cz * WorldConstants.CHUNK_SIZE
            val masterIdx = Chunk.index(localX, masterWorldPos.y, localZ)
            affectedChunks[cp] = Pair(existing.removeEntity(masterIdx), topY)
        }

        msg.entityRemovesAt.forEach { spec ->
            val cx = spec.pos.x.floorDiv(WorldConstants.CHUNK_SIZE)
            val cz = spec.pos.z.floorDiv(WorldConstants.CHUNK_SIZE)
            val cp = ChunkPos(cx, cz)
            val (existing, topY) =
                affectedChunks[cp] ?: chunkManager.chunkData[cp] ?: return@forEach
            val localX = spec.pos.x - cx * WorldConstants.CHUNK_SIZE
            val localZ = spec.pos.z - cz * WorldConstants.CHUNK_SIZE
            val masterIdx = Chunk.index(localX, spec.pos.y, localZ)
            affectedChunks[cp] =
                Pair(
                    existing.removeEntityAt(masterIdx, spec.yOffset, spec.xOffset, spec.zOffset),
                    topY)
        }

        affectedChunks.forEach { (_, pair) ->
            chunkManager.updateAndEnqueue(pair.first, pair.second)
        }
    }
}
