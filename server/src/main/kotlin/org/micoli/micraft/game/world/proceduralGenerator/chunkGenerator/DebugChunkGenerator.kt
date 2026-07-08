package org.micoli.micraft.game.world.proceduralGenerator.chunkGenerator

import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.Chunk
import org.micoli.micraft.game.world.ChunkPos

/** Single GRASS block at world (8, 2, 8) — all other positions are AIR. */
class DebugChunkGenerator : ChunkGenerator {
    override fun generate(pos: ChunkPos): Chunk =
        Chunk.build(pos) { x, y, z ->
            if (pos.cx == 0 && pos.cz == 0 && x == 8 && y == 2 && z == 8) BlockType.GRASS
            else BlockType.AIR
        }
}
