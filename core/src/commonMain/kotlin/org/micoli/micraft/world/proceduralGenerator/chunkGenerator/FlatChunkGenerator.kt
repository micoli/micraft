package org.micoli.micraft.world.proceduralGenerator.chunkGenerator

import org.micoli.micraft.world.BlockType
import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.ChunkPos

class FlatChunkGenerator : ChunkGenerator {
    override fun generate(pos: ChunkPos): Chunk =
        Chunk.Companion.build(pos) { _, y, _ ->
            when (y) {
                0 -> BlockType.BEDROCK
                in 1..4 -> BlockType.STONE
                in 5..6 -> BlockType.DIRT
                7 -> BlockType.GRASS
                else -> BlockType.AIR
            }
        }
}
