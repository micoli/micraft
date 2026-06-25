package org.micoli.micraft.world.proceduralGenerator.chunkGenerator

import org.micoli.micraft.world.Chunk
import org.micoli.micraft.world.ChunkPos

interface ChunkGenerator {
    fun generate(pos: ChunkPos): Chunk

    fun biomeAt(wx: Int, wz: Int): String = ""
}
