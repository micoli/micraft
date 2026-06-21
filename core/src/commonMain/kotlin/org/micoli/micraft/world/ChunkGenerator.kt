package org.micoli.micraft.world

interface ChunkGenerator {
    fun generate(pos: ChunkPos): Chunk
    fun biomeAt(wx: Int, wz: Int): String = ""
}
