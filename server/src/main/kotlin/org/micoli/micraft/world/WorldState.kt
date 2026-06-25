package org.micoli.micraft.world

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.slf4j.LoggerFactory

class WorldState(
    @Volatile var generator: ChunkGenerator,
    val persistence: WorldPersistence? = null,
) {
    private val chunks = ConcurrentHashMap<ChunkPos, Chunk>()
    private val dirtyChunks: MutableSet<ChunkPos> = Collections.newSetFromMap(ConcurrentHashMap())
    private val log = LoggerFactory.getLogger("WorldState")

    fun getOrGenerate(pos: ChunkPos): Chunk =
        chunks.getOrPut(pos) {
            val loaded = persistence?.loadChunk(pos)
            if (loaded != null) {
                    log.debug("Loaded chunk {} from disk", pos)
                    loaded
                } else {
                    val t0 = System.currentTimeMillis()
                    val chunk = generator.generate(pos)
                    log.info("Generated chunk {} in {}ms", pos, System.currentTimeMillis() - t0)
                    chunk
                }
                .also { dirtyChunks.add(pos) }
        }

    fun biomeAt(wx: Int, wz: Int): String = generator.biomeAt(wx, wz)

    fun getBlock(wx: Int, wy: Int, wz: Int): BlockType {
        if (wy < WorldConstants.WORLD_MIN_Y || wy > WorldConstants.WORLD_MAX_Y) return BlockType.AIR
        val chunkX = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(wx, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(wz, WorldConstants.CHUNK_SIZE)
        return getOrGenerate(ChunkPos(chunkX, chunkZ)).getBlock(localX, wy, localZ)
    }

    /**
     * All chunk positions that have ever been generated or loaded (i.e. discovered by a player).
     */
    fun discoveredChunks(): Set<ChunkPos> = chunks.keys.toSet()

    /** Returns a chunk only if it was already generated — never triggers generation. */
    fun getChunkIfDiscovered(pos: ChunkPos): Chunk? = chunks[pos]

    /**
     * Returns the block type without generating the chunk if absent — returns AIR for ungenerated
     * chunks.
     */
    fun getBlockIfLoaded(wx: Int, wy: Int, wz: Int): BlockType {
        if (wy < WorldConstants.WORLD_MIN_Y || wy > WorldConstants.WORLD_MAX_Y) return BlockType.AIR
        val chunkX = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(wx, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(wz, WorldConstants.CHUNK_SIZE)
        return chunks[ChunkPos(chunkX, chunkZ)]?.getBlock(localX, wy, localZ) ?: BlockType.AIR
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
        if (saved > 0) log.info("Flushed {} dirty chunks to disk", saved)
    }
}
