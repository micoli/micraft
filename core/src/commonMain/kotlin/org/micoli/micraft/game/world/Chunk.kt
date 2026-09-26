package org.micoli.micraft.game.world

import org.micoli.micraft.protocol.BlockEntityProto

/**
 * One chunk column. Blocks, states and extra states are [SectionedBytes] in y-major order (the wire
 * order too): only the sections holding something are allocated, and a chunk is never mutated once
 * built — [withBlock] returns a copy sharing the untouched sections.
 */
data class Chunk(
    val pos: ChunkPos,
    val blocks: SectionedBytes,
    val states: SectionedBytes = SectionedBytes(),
    val extraStates: SectionedBytes = SectionedBytes(),
    val entityMasters: List<BlockEntity> = emptyList(),
) {
    // Not part of the constructor, so `copy()`/`withBlock()` always hand back a fresh, uncached
    // instance — cheap correctness by construction instead of manual invalidation.
    private var cachedTopY: Int = -1

    companion object {
        val SIZE_X = WorldConstants.CHUNK_SIZE
        val SIZE_Z = WorldConstants.CHUNK_SIZE
        val SIZE_Y = WorldConstants.WORLD_MAX_Y + 1
        val TOTAL = SIZE_X * SIZE_Y * SIZE_Z
        private val LAYER = SIZE_X * SIZE_Z

        /** 16 whole Y layers: 4 096 cells for 16×16 chunks. */
        val SECTION_SIZE = LAYER * 16

        /** Y-major: one Y layer is a contiguous run, as on the wire. */
        fun index(x: Int, y: Int, z: Int) = (y * LAYER) + (x * SIZE_Z) + z

        /** [index] offset of the x+1 neighbour (z+1 is +1). */
        val STRIDE_X = SIZE_Z

        /** [index] offset of the y+1 neighbour. */
        val STRIDE_Y = LAYER

        fun indexToXYZ(idx: Int): Triple<Int, Int, Int> {
            val y = idx / LAYER
            val rem = idx % LAYER
            val x = rem / SIZE_Z
            val z = rem % SIZE_Z
            return Triple(x, y, z)
        }

        fun empty(pos: ChunkPos) = Chunk(pos, SectionedBytes())

        /** A chunk from flat y-major arrays ([index] order), as the generators fill them. */
        fun of(
            pos: ChunkPos,
            blocks: ByteArray,
            states: ByteArray? = null,
            extraStates: ByteArray? = null,
            entityMasters: List<BlockEntity> = emptyList(),
        ) =
            Chunk(
                pos,
                SectionedBytes.of(blocks),
                states?.let { SectionedBytes.of(it) } ?: SectionedBytes(),
                extraStates?.let { SectionedBytes.of(it) } ?: SectionedBytes(),
                entityMasters,
            )

        fun build(pos: ChunkPos, filler: (x: Int, y: Int, z: Int) -> BlockType): Chunk {
            val blocks = ByteArray(TOTAL)
            for (x in 0 until SIZE_X) for (y in 0 until SIZE_Y) for (z in 0 until SIZE_Z) blocks[
                index(x, y, z)] = BlockRegistry.wireIndex(filler(x, y, z)).toByte()
            return of(pos, blocks)
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

        /**
         * Decode a wire buffer (produced by encodeWire) back into a Chunk. The wire is the y-major
         * prefix up to [topY], i.e. the storage order itself; AIR (0) fills the rest.
         */
        fun decodeWire(
            pos: ChunkPos,
            topY: Int,
            wire: ByteArray,
            wireStates: ByteArray? = null,
            wireExtraStates: ByteArray? = null,
            entityProtos: List<BlockEntityProto> = emptyList(),
        ): Chunk {
            val length = minOf(wire.size, (topY + 1) * LAYER)
            val blocks = SectionedBytes.of(if (wire.size > length) wire.copyOf(length) else wire)
            val states = wireStates?.let { SectionedBytes.of(it) } ?: SectionedBytes()
            val extraStates = wireExtraStates?.let { SectionedBytes.of(it) } ?: SectionedBytes()
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
            return Chunk(pos, blocks, states, extraStates, masters)
        }
    }

    fun getBlock(x: Int, y: Int, z: Int): BlockType =
        BlockRegistry.byWireIndex(blocks[index(x, y, z)].toInt() and 0xFF)

    fun getState(x: Int, y: Int, z: Int): Byte = states[index(x, y, z)]

    fun getExtraState(x: Int, y: Int, z: Int): Byte = extraStates[index(x, y, z)]

    /** Highest Y level containing any non-AIR block (AIR is wire index 0), 0 for an empty chunk. */
    fun topY(): Int {
        cachedTopY.let { if (it >= 0) return it }
        val top = maxOf(0, blocks.highestNonZeroIndex() / LAYER)
        cachedTopY = top
        return top
    }

    private fun wirePrefix() = (topY() + 1) * LAYER

    /** Encode blocks in y-major order, only y=0..topY (88% smaller than full chunk). */
    fun encodeWire(): ByteArray = blocks.toByteArray(wirePrefix())

    /** Encode states in y-major order, only y=0..topY. Returns null if all states are zero. */
    fun encodeWireStates(): ByteArray? =
        if (states.isAllZero()) null else states.toByteArray(wirePrefix())

    /** Encode extra states in y-major order, only y=0..topY. Returns null if all zero. */
    fun encodeWireExtraStates(): ByteArray? =
        if (extraStates.isAllZero()) null else extraStates.toByteArray(wirePrefix())

    fun withBlock(
        x: Int,
        y: Int,
        z: Int,
        type: BlockType,
        state: Byte = 0,
        extraState: Byte = 0
    ): Chunk {
        val i = index(x, y, z)
        return copy(
            blocks = blocks.withByte(i, BlockRegistry.wireIndex(type).toByte()),
            states = states.withByte(i, state),
            extraStates = extraStates.withByte(i, extraState),
        )
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
            extraStates.contentEquals(other.extraStates) &&
            entityMasters == other.entityMasters
    }

    override fun hashCode(): Int {
        var result =
            31 * (31 * pos.hashCode() + blocks.contentHashCode()) + states.contentHashCode()
        result = 31 * result + extraStates.contentHashCode()
        result = 31 * result + entityMasters.hashCode()
        return result
    }
}
