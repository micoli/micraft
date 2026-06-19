package org.micoli.micraft.world

import java.util.concurrent.ConcurrentHashMap

class WorldState(private val generator: ChunkGenerator) {
    private val chunks = ConcurrentHashMap<ChunkPos, Chunk>()

    fun getOrGenerate(pos: ChunkPos): Chunk =
        chunks.getOrPut(pos) { generator.generate(pos) }

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
    }
}
