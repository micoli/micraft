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
    }

    fun getBlock(x: Int, y: Int, z: Int): BlockType =
        BlockType.entries[blocks[index(x, y, z)].toInt()]

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
