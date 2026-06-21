package org.micoli.micraft.world

class ProceduralChunkGenerator(
    seed: Long = 42L,
    private val biomeRegistry: BiomeRegistry = BiomeRegistry.default(),
) : ChunkGenerator {
    private val elevationNoise = PerlinNoise(seed)
    private val moistureNoise  = PerlinNoise(seed + 1L)
    private val voronoi        = VoronoiBiomeZones(seed, biomeRegistry, moistureNoise)

    fun surfaceHeight(wx: Int, wz: Int): Int {
        val n = elevationNoise.octaveNoise(wx / 64.0, wz / 64.0, octaves = 6, persistence = 0.5)
        return ((n + 1.0) / 2.0 * 80.0 + 40.0).toInt()
            .coerceIn(4, WorldConstants.WORLD_MAX_Y - 1)
    }

    override fun generate(pos: ChunkPos): Chunk {
        val ox = pos.cx * WorldConstants.CHUNK_SIZE
        val oz = pos.cz * WorldConstants.CHUNK_SIZE
        val s  = WorldConstants.CHUNK_SIZE

        // Precompute per-column surface/biome data (16×16 = 256 calls instead of 262 400)
        val cols = Array(s) { x ->
            Array(s) { z ->
                val wx = ox + x; val wz = oz + z
                val h = surfaceHeight(wx, wz)
                ColumnData(h, voronoi.selectColumn(wx, wz, h))
            }
        }

        return Chunk.build(pos) { x, y, z ->
            val col = cols[x][z]
            val h   = col.h
            val b   = col.blocks
            when {
                y == 0                          -> BlockType.BEDROCK
                y == h                          -> b.surface
                y > h - b.subsurfaceDepth && y < h -> b.subsurface
                y < h                           -> BlockType.STONE
                else                            -> BlockType.AIR
            }
        }
    }

    override fun biomeAt(wx: Int, wz: Int): String = voronoi.sample(wx, wz).primary.id

    private data class ColumnData(val h: Int, val blocks: VoronoiBiomeZones.ColumnBlocks)
}
