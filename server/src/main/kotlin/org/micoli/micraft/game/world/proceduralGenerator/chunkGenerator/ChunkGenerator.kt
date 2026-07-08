package org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator

import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos

interface ChunkGenerator {
    fun generate(pos: ChunkPos): Chunk

    fun biomeAt(wx: Int, wz: Int): String = ""
}
