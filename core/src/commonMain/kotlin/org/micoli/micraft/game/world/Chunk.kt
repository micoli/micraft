package org.micoli.micraft.game.world

import kotlinx.serialization.Serializable

@Serializable
data class Chunk(
    val pos: ChunkPos,
    val blocks: ByteArray,
    val states: ByteArray = ByteArray(0),
    val entityMasters: List<BlockEntity> = emptyList(),
) {
    companion object {
        val SIZE_X = WorldConstants.CHUNK_SIZE
        val SIZE_Z = WorldConstants.CHUNK_SIZE
        val SIZE_Y = WorldConstants.WORLD_MAX_Y + 1
        val TOTAL = SIZE_X * SIZE_Y * SIZE_Z

        fun index(x: Int, y: Int, z: Int) = (x * SIZE_Y * SIZE_Z) + (y * SIZE_Z) + z

        fun indexToXYZ(idx: Int): Triple<Int, Int, Int> {
            val yz = SIZE_Y * SIZE_Z
            val x = idx / yz
            val rem = idx % yz
            val y = rem / SIZE_Z
            val z = rem % SIZE_Z
            return Triple(x, y, z)
        }

        fun empty(pos: ChunkPos) = Chunk(pos, ByteArray(TOTAL), ByteArray(TOTAL))

        fun build(pos: ChunkPos, filler: (x: Int, y: Int, z: Int) -> BlockType): Chunk {
            val blocks = ByteArray(TOTAL)
            for (x in 0 until SIZE_X) for (y in 0 until SIZE_Y) for (z in 0 until SIZE_Z) blocks[
                index(x, y, z)] = BlockRegistry.wireIndex(filler(x, y, z)).toByte()
            return Chunk(pos, blocks, ByteArray(TOTAL))
        }

        /**
         * Reconstruct entity map (cell → entities occupying it) from master list. A value list
         * holds more than one entry when several fractional entities (XZ sub-slots and/or Y stacks)
         * share the same voxel — e.g. multiple LEGO_PIECE instances in one cell.
         */
        fun buildEntitiesMap(masters: List<BlockEntity>): Map<Int, List<BlockEntity>> {
            if (masters.isEmpty()) return emptyMap()
            val map = mutableMapOf<Int, MutableList<BlockEntity>>()
            for (entity in masters) {
                val (mx, my, mz) = indexToXYZ(entity.masterIdx)
                for (dx in 0 until entity.sizeX) for (dy in 0 until entity.sizeY) for (dz in
                    0 until entity.sizeZ) {
                    val nx = mx + dx
                    val ny = my + dy
                    val nz = mz + dz
                    if (nx < SIZE_X && ny < SIZE_Y && nz < SIZE_Z)
                        map.getOrPut(index(nx, ny, nz)) { mutableListOf() }.add(entity)
                }
            }
            return map
        }

        /** Decode a y-major wire buffer (produced by encodeWire) back into a full Chunk. */
        fun decodeWire(
            pos: ChunkPos,
            topY: Int,
            wire: ByteArray,
            wireStates: ByteArray? = null,
            entityProtos: List<org.micoli.micraft.protocol.BlockEntityProto> = emptyList(),
        ): Chunk {
            val blocks = ByteArray(TOTAL) // AIR = 0 by default
            val states = ByteArray(TOTAL)
            for (y in 0..topY) for (x in 0 until SIZE_X) for (z in 0 until SIZE_Z) {
                val wi = y * SIZE_X * SIZE_Z + x * SIZE_Z + z
                blocks[index(x, y, z)] = wire[wi]
                if (wireStates != null && wi < wireStates.size)
                    states[index(x, y, z)] = wireStates[wi]
            }
            val masters =
                entityProtos.map { proto ->
                    val chunkX = pos.cx * SIZE_X
                    val chunkZ = pos.cz * SIZE_Z
                    val lx = proto.worldX - chunkX
                    val lz = proto.worldZ - chunkZ
                    BlockEntity(
                        masterIdx = index(lx, proto.worldY, lz),
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
                }
            return Chunk(pos, blocks, states, masters)
        }
    }

    fun getBlock(x: Int, y: Int, z: Int): BlockType =
        BlockRegistry.byWireIndex(blocks[index(x, y, z)].toInt() and 0xFF)

    fun getState(x: Int, y: Int, z: Int): Byte =
        if (states.isNotEmpty()) states[index(x, y, z)] else 0

    /** Highest Y level containing any non-AIR block. */
    fun topY(): Int {
        var top = 0
        for (x in 0 until SIZE_X) for (z in 0 until SIZE_Z) for (y in SIZE_Y - 1 downTo 0) {
            if (getBlock(x, y, z) != BlockType.AIR) {
                if (y > top) top = y
                break
            }
        }
        return top
    }

    /** Encode blocks in y-major order, only y=0..topY (88% smaller than full chunk). */
    fun encodeWire(): ByteArray {
        val top = topY()
        val wire = ByteArray((top + 1) * SIZE_X * SIZE_Z)
        for (y in 0..top) for (x in 0 until SIZE_X) for (z in 0 until SIZE_Z) wire[
            y * SIZE_X * SIZE_Z + x * SIZE_Z + z] = blocks[index(x, y, z)]
        return wire
    }

    /** Encode states in y-major order, only y=0..topY. Returns null if all states are zero. */
    fun encodeWireStates(): ByteArray? {
        if (states.isEmpty() || states.all { it == 0.toByte() }) return null
        val top = topY()
        val wire = ByteArray((top + 1) * SIZE_X * SIZE_Z)
        for (y in 0..top) for (x in 0 until SIZE_X) for (z in 0 until SIZE_Z) wire[
            y * SIZE_X * SIZE_Z + x * SIZE_Z + z] = states[index(x, y, z)]
        return wire
    }

    fun withBlock(x: Int, y: Int, z: Int, type: BlockType, state: Byte = 0): Chunk {
        val newBlocks = blocks.copyOf()
        val newStates = if (states.isNotEmpty()) states.copyOf() else ByteArray(TOTAL)
        newBlocks[index(x, y, z)] = BlockRegistry.wireIndex(type).toByte()
        newStates[index(x, y, z)] = state
        return copy(blocks = newBlocks, states = newStates)
    }

    fun addEntity(entity: BlockEntity): Chunk = copy(entityMasters = entityMasters + entity)

    fun removeEntity(masterIdx: Int): Chunk =
        copy(entityMasters = entityMasters.filter { it.masterIdx != masterIdx })

    fun removeEntityAt(masterIdx: Int, yOffset: Int, xOffset: Int = 0, zOffset: Int = 0): Chunk =
        copy(
            entityMasters =
                entityMasters.filter {
                    !(it.masterIdx == masterIdx &&
                        it.yOffset == yOffset &&
                        it.xOffset == xOffset &&
                        it.zOffset == zOffset)
                })

    fun buildEntitiesMap(): Map<Int, List<BlockEntity>> = Companion.buildEntitiesMap(entityMasters)

    fun isMasterAt(idx: Int): Boolean = entityMasters.any { it.masterIdx == idx }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Chunk) return false
        return pos == other.pos &&
            blocks.contentEquals(other.blocks) &&
            states.contentEquals(other.states) &&
            entityMasters == other.entityMasters
    }

    override fun hashCode(): Int {
        var result =
            31 * (31 * pos.hashCode() + blocks.contentHashCode()) + states.contentHashCode()
        result = 31 * result + entityMasters.hashCode()
        return result
    }
}
