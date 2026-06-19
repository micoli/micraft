package org.micoli.micraft.world

class FlatChunkGenerator : ChunkGenerator {
    override fun generate(pos: ChunkPos): Chunk = Chunk.build(pos) { _, y, _ ->
        when (y) {
            0        -> BlockType.BEDROCK
            in 1..4  -> BlockType.STONE
            in 5..6  -> BlockType.DIRT
            7        -> BlockType.GRASS
            else     -> BlockType.AIR
        }
    }
}
