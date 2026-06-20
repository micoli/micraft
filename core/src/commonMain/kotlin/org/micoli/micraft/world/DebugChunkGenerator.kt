package org.micoli.micraft.world

/** Single GRASS block at world (8, 2, 8) — all other positions are AIR. */
class DebugChunkGenerator : ChunkGenerator {
    override fun generate(pos: ChunkPos): Chunk = Chunk.build(pos) { x, y, z ->
        if (pos.cx == 0 && pos.cz == 0 && x == 8 && y == 2 && z == 8) BlockType.GRASS
        else BlockType.AIR
    }
}
