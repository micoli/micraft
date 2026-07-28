package org.micoli.micraft.game.world

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator.ChunkGenerator
import org.micoli.micraft.player.Vec3
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.BlockEntityProto
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

    fun biomeDefinitionAt(wx: Int, wz: Int) = generator.biomeDefinitionAt(wx, wz)

    fun zoneLevelAt(wx: Int, wz: Int): Int = generator.zoneLevelAt(wx, wz)

    fun getBlockBelow(pos: Vec3): BlockType {
        return getBlockBelow(pos.x.toInt(), pos.y.toInt(), pos.z.toInt())
    }

    fun getBlockBelow(wx: Int, wy: Int, wz: Int): BlockType {
        return getBlock(wx, (wy - 0.1f).toInt(), wz)
    }

    fun getBlock(pos: Vec3): BlockType {
        return getBlock(pos.x.toInt(), pos.y.toInt(), pos.z.toInt())
    }

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
    fun loadedChunkCount(): Int = chunks.size

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

    fun getState(wx: Int, wy: Int, wz: Int): Byte {
        if (wy < WorldConstants.WORLD_MIN_Y || wy > WorldConstants.WORLD_MAX_Y) return 0
        val chunkX = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(wx, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(wz, WorldConstants.CHUNK_SIZE)
        return chunks[ChunkPos(chunkX, chunkZ)]?.getState(localX, wy, localZ) ?: 0
    }

    fun hasEntityAt(wx: Int, wy: Int, wz: Int): Boolean {
        if (wy < WorldConstants.WORLD_MIN_Y || wy > WorldConstants.WORLD_MAX_Y) return false
        val chunkX = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(wx, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(wz, WorldConstants.CHUNK_SIZE)
        val idx = Chunk.index(localX, wy, localZ)
        return chunks[ChunkPos(chunkX, chunkZ)]?.buildEntitiesMap()?.containsKey(idx) == true
    }

    fun isSolidOrOccupied(wx: Int, wy: Int, wz: Int): Boolean =
        getBlock(wx, wy, wz).isSolid || hasEntityAt(wx, wy, wz)

    fun applyChange(change: BlockChange) {
        val chunkX = Math.floorDiv(change.pos.x, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(change.pos.z, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(change.pos.x, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(change.pos.z, WorldConstants.CHUNK_SIZE)
        val pos = ChunkPos(chunkX, chunkZ)
        val chunk = getOrGenerate(pos)
        chunks[pos] = chunk.withBlock(localX, change.pos.y, localZ, change.type, change.state)
        dirtyChunks.add(pos)
    }

    fun applyEntityAdd(proto: BlockEntityProto) {
        val chunkX = Math.floorDiv(proto.worldX, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(proto.worldZ, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(proto.worldX, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(proto.worldZ, WorldConstants.CHUNK_SIZE)
        val cPos = ChunkPos(chunkX, chunkZ)
        val chunk = getOrGenerate(cPos)
        val masterIdx = Chunk.index(localX, proto.worldY, localZ)
        val entity =
            BlockEntity(
                masterIdx = masterIdx,
                type = BlockType(proto.type),
                sizeX = proto.sizeX,
                sizeY = proto.sizeY,
                sizeZ = proto.sizeZ,
                rotation = proto.rotation,
            )
        chunks[cPos] = chunk.addEntity(entity)
        dirtyChunks.add(cPos)
    }

    fun applyEntityRemove(worldMasterPos: BlockPos) {
        val chunkX = Math.floorDiv(worldMasterPos.x, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(worldMasterPos.z, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(worldMasterPos.x, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(worldMasterPos.z, WorldConstants.CHUNK_SIZE)
        val cPos = ChunkPos(chunkX, chunkZ)
        val chunk = chunks[cPos] ?: return
        val masterIdx = Chunk.index(localX, worldMasterPos.y, localZ)
        chunks[cPos] = chunk.removeEntity(masterIdx)
        dirtyChunks.add(cPos)
    }

    /** Returns world-coordinate BlockPos of master entity at given world position, or null. */
    fun getEntityMasterWorldPos(wx: Int, wy: Int, wz: Int): BlockPos? {
        if (wy < WorldConstants.WORLD_MIN_Y || wy > WorldConstants.WORLD_MAX_Y) return null
        val chunkX = Math.floorDiv(wx, WorldConstants.CHUNK_SIZE)
        val chunkZ = Math.floorDiv(wz, WorldConstants.CHUNK_SIZE)
        val localX = Math.floorMod(wx, WorldConstants.CHUNK_SIZE)
        val localZ = Math.floorMod(wz, WorldConstants.CHUNK_SIZE)
        val cPos = ChunkPos(chunkX, chunkZ)
        val chunk = chunks[cPos] ?: return null
        val idx = Chunk.index(localX, wy, localZ)
        val entity = chunk.buildEntitiesMap()[idx] ?: return null
        val (mx, my, mz) = Chunk.indexToXYZ(entity.masterIdx)
        return BlockPos(
            chunkX * WorldConstants.CHUNK_SIZE + mx,
            my,
            chunkZ * WorldConstants.CHUNK_SIZE + mz,
        )
    }

    /** All master entities in a chunk as proto (world coords). */
    fun chunkEntityProtos(cPos: ChunkPos): List<BlockEntityProto> {
        val chunk = chunks[cPos] ?: return emptyList()
        return chunk.entityMasters.map { e ->
            val (lx, ly, lz) = Chunk.indexToXYZ(e.masterIdx)
            BlockEntityProto(
                worldX = cPos.cx * WorldConstants.CHUNK_SIZE + lx,
                worldY = ly,
                worldZ = cPos.cz * WorldConstants.CHUNK_SIZE + lz,
                type = e.type.id,
                sizeX = e.sizeX,
                sizeY = e.sizeY,
                sizeZ = e.sizeZ,
                rotation = e.rotation,
            )
        }
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
