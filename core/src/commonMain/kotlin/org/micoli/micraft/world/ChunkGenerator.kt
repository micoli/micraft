package org.micoli.micraft.world

interface ChunkGenerator {
    fun generate(pos: ChunkPos): Chunk
}
