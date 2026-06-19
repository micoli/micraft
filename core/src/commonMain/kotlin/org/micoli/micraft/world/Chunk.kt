package org.micoli.micraft.world

import kotlinx.serialization.Serializable

@Serializable
data class ChunkPos(val cx: Int, val cz: Int)

@Serializable
data class Chunk(val pos: ChunkPos, val blocks: ByteArray) {
    companion object {
        val SIZE_X = WorldConstants.CHUNK_SIZE
        val SIZE_Z = WorldConstants.CHUNK_SIZE
        val SIZE_Y = WorldConstants.WORLD_MAX_Y + 1
        val TOTAL = SIZE_X * SIZE_Y * SIZE_Z

        fun index(x: Int, y: Int, z: Int) = (x * SIZE_Y * SIZE_Z) + (y * SIZE_Z) + z
        fun empty(pos: ChunkPos) = Chunk(pos, ByteArray(TOTAL) { BlockType.AIR.ordinal.toByte() })

        fun build(pos: ChunkPos, filler: (x: Int, y: Int, z: Int) -> BlockType): Chunk {
            val blocks = ByteArray(TOTAL)
            for (x in 0 until SIZE_X)
                for (y in 0 until SIZE_Y)
                    for (z in 0 until SIZE_Z)
                        blocks[index(x, y, z)] = filler(x, y, z).ordinal.toByte()
            return Chunk(pos, blocks)
        }

        /** Decode a y-major wire buffer (produced by encodeWire) back into a full Chunk. */
        fun decodeWire(pos: ChunkPos, topY: Int, wire: ByteArray): Chunk {
            val blocks = ByteArray(TOTAL)   // AIR = 0 by default
            for (y in 0..topY)
                for (x in 0 until SIZE_X)
                    for (z in 0 until SIZE_Z)
                        blocks[index(x, y, z)] =
                            wire[y * SIZE_X * SIZE_Z + x * SIZE_Z + z]
            return Chunk(pos, blocks)
        }
    }

    fun getBlock(x: Int, y: Int, z: Int): BlockType =
        BlockType.entries[blocks[index(x, y, z)].toInt()]

    /** Highest Y level containing any non-AIR block. */
    fun topY(): Int {
        var top = 0
        for (x in 0 until SIZE_X)
            for (z in 0 until SIZE_Z)
                for (y in SIZE_Y - 1 downTo 0) {
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
        for (y in 0..top)
            for (x in 0 until SIZE_X)
                for (z in 0 until SIZE_Z)
                    wire[y * SIZE_X * SIZE_Z + x * SIZE_Z + z] = blocks[index(x, y, z)]
        return wire
    }

    fun withBlock(x: Int, y: Int, z: Int, type: BlockType): Chunk {
        val newBlocks = blocks.copyOf()
        newBlocks[index(x, y, z)] = type.ordinal.toByte()
        return copy(blocks = newBlocks)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Chunk) return false
        return pos == other.pos && blocks.contentEquals(other.blocks)
    }

    override fun hashCode(): Int = 31 * pos.hashCode() + blocks.contentHashCode()
}
