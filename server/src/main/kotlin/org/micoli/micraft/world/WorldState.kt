package org.micoli.micraft.world

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class WorldState(
    private val generator: ChunkGenerator,
    val persistence: WorldPersistence? = null,
) {
    private val chunks = ConcurrentHashMap<ChunkPos, Chunk>()
    private val dirtyChunks: MutableSet<ChunkPos> = Collections.newSetFromMap(ConcurrentHashMap())

    fun getOrGenerate(pos: ChunkPos): Chunk =
        chunks.getOrPut(pos) {
            (persistence?.loadChunk(pos) ?: generator.generate(pos)).also {
                dirtyChunks.add(pos)
            }
        }

    fun getBlock(wx: Int, wy: Int, wz: Int): BlockType {
        if (wy < WorldConstants.WORLD_MIN_Y || wy > WorldConstants.WORLD_MAX_Y) return BlockType.AIR
        val chunkX = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(wx, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(wz, WorldConstants.CHUNK_SIZE)
        return getOrGenerate(ChunkPos(chunkX, chunkZ)).getBlock(localX, wy, localZ)
    }

    fun applyChange(change: org.micoli.micraft.protocol.BlockChange) {
        val chunkX = Math.floorDiv(change.pos.x, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(change.pos.z, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(change.pos.x, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(change.pos.z, WorldConstants.CHUNK_SIZE)
        val pos = ChunkPos(chunkX, chunkZ)
        val chunk = getOrGenerate(pos)
        chunks[pos] = chunk.withBlock(localX, change.pos.y, localZ, change.type)
        dirtyChunks.add(pos)
    }

    fun flushDirty() {
        if (persistence == null) return
        val toSave = dirtyChunks.toSet()
        if (toSave.isEmpty()) return
        dirtyChunks.removeAll(toSave)
        var saved = 0
        toSave.forEach { pos ->
            chunks[pos]?.let {
                persistence.saveChunk(pos, it)
                saved++
            }
        }
        if (saved > 0) org.slf4j.LoggerFactory.getLogger("WorldState")
            .info("Flushed {} dirty chunks to disk", saved)
    }
}
