package org.micoli.micraft.world

class ProceduralChunkGenerator(seed: Long = 42L) : ChunkGenerator {
    private val noise = PerlinNoise(seed)

    // Returns surface height (index of the grass block) for world coordinates (wx, wz).
    fun surfaceHeight(wx: Int, wz: Int): Int {
        val n = noise.octaveNoise(wx / 64.0, wz / 64.0, octaves = 6, persistence = 0.5)
        // Map [-1, 1] → [40, 120], clamp within world bounds
        return ((n + 1.0) / 2.0 * 80.0 + 40.0).toInt()
            .coerceIn(4, WorldConstants.WORLD_MAX_Y - 1)
    }

    override fun generate(pos: ChunkPos): Chunk {
        val ox = pos.cx * WorldConstants.CHUNK_SIZE
        val oz = pos.cz * WorldConstants.CHUNK_SIZE
        return Chunk.build(pos) { x, y, z ->
            val h = surfaceHeight(ox + x, oz + z)
            when {
                y == 0          -> BlockType.BEDROCK
                y < h - 3       -> BlockType.STONE
                y < h           -> BlockType.DIRT
                y == h          -> BlockType.GRASS
                else            -> BlockType.AIR
            }
        }
    }
}
